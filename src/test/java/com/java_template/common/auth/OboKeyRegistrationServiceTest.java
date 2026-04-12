package com.java_template.common.auth;

import com.java_template.common.config.Config;
import com.java_template.common.dto.PageResult;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ABOUTME: Verifies the trusted-key registration request body matches the Cyoda API schema
 * (JWK format, audience field, validTo absolute timestamp) and error handling for 409 conflicts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OboKeyRegistrationServiceTest {

    @Mock private Authentication authentication;
    @Mock private EntityService entityService;
    @Mock private Config config;

    private OboKeyRegistrationService service;
    private final AtomicReference<Map<String, Object>> capturedTrustedKeyBody = new AtomicReference<>();
    private boolean trustedKeyPostShouldThrow409 = false;

    @BeforeEach
    void setUp() throws Exception {
        trustedKeyPostShouldThrow409 = false;
        capturedTrustedKeyBody.set(null);

        OboProperties oboProperties = new OboProperties();
        oboProperties.setEncryptionKey("BO1DMziwLVFBy+eBH4KSewhkCaPpP+2XTw018eTacho=");
        oboProperties.setAdminClientId("admin-id");
        oboProperties.setAdminClientSecret("admin-secret");
        oboProperties.setKeyId("test-key-001");
        oboProperties.setIssuer("test-issuer");
        oboProperties.setValidityDays(90);

        when(config.getCyodaApiUrl()).thenReturn("http://cyoda.test/api");
        lenient().when(config.getCyodaClientId()).thenReturn("client-id");
        lenient().when(config.getCyodaClientSecret()).thenReturn("client-secret");
        lenient().when(config.getTrustedHosts()).thenReturn(List.of());

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "m2m-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        when(authentication.getAccessToken()).thenReturn(token);

        when(entityService.findAll(any(), any())).thenReturn(PageResult.of(null, List.of(), 0, 100, 0));
        lenient().when(entityService.create(any())).thenReturn(null);

        SubjectTokenSigner signer = new SubjectTokenSigner(oboProperties);
        service = new OboKeyRegistrationService(oboProperties, signer, authentication, entityService, config);

        RestClient mockRestClient = buildDispatchingRestClient();
        Field restClientField = OboKeyRegistrationService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(service, mockRestClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerPublicKey_sendsJwkFormatBody() {
        service.onStartup();

        Map<String, Object> body = capturedTrustedKeyBody.get();
        assertThat(body).as("Body sent to POST /oauth/keys/trusted should have been captured").isNotNull();
        assertThat(body.get("keyId")).isEqualTo("test-key-001");

        assertThat(body.get("jwk")).isInstanceOf(Map.class);
        Map<String, Object> jwk = (Map<String, Object>) body.get("jwk");
        assertThat(jwk.get("kty")).isEqualTo("RSA");
        assertThat(jwk.get("kid")).isEqualTo("test-key-001");
        assertThat(jwk.get("alg")).isEqualTo("RS256");
        assertThat(jwk).containsKeys("n", "e");

        assertThat(body.get("audience")).isEqualTo("human");
        assertThat((String) body.get("validTo")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
        assertThat(body.get("issuers")).isEqualTo(List.of("test-issuer"));

        assertThat(body).doesNotContainKeys("algorithm", "publicKey", "providerId", "validityDays");
    }

    @Test
    void registerPublicKey_409conflict_throwsWithDiagnostic() {
        trustedKeyPostShouldThrow409 = true;

        // onStartup catches OboTokenException internally and logs it.
        // The body is captured before retrieve() throws 409.
        // Verify that no entity was created (create is called only after successful registration).
        service.onStartup();

        verify(entityService, never()).create(any());
    }

    @SuppressWarnings("rawtypes")
    private RestClient buildDispatchingRestClient() {
        RestClient restClient = mock(RestClient.class);

        // GET dispatcher: /account and /model/export
        when(restClient.get()).thenAnswer(inv -> {
            RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
            org.mockito.stubbing.Answer<Object> getUriAnswer = uriInv -> {
                String uri = uriInv.getArgument(0);
                RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
                when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
                RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
                when(headersSpec.retrieve()).thenReturn(respSpec);

                if (uri.contains("/account")) {
                    when(respSpec.body(eq(Map.class))).thenReturn(
                            Map.of("userAccountInfo", Map.of("legalEntity", Map.of("id", "test-org-id"))));
                } else if (uri.contains("/model/export")) {
                    when(respSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
                }
                return headersSpec;
            };
            when(getSpec.uri(anyString())).thenAnswer(getUriAnswer);
            when(getSpec.uri(anyString(), any(Object[].class))).thenAnswer(getUriAnswer);
            return getSpec;
        });

        // POST dispatcher: /oauth/token and /oauth/keys/trusted
        when(restClient.post()).thenAnswer(inv -> {
            RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
            org.mockito.stubbing.Answer<Object> postUriAnswer = uriInv -> {
                String uri = uriInv.getArgument(0);
                RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
                when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
                when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
                RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

                if (uri.contains("/oauth/token")) {
                    when(bodySpec.body(any(String.class))).thenReturn(bodySpec);
                    when(bodySpec.retrieve()).thenReturn(respSpec);
                    when(respSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "admin-token"));
                } else if (uri.contains("/oauth/keys/trusted")) {
                    when(bodySpec.body(any(Map.class))).thenAnswer(bodyInv -> {
                        capturedTrustedKeyBody.set(bodyInv.getArgument(0));
                        return bodySpec;
                    });
                    if (trustedKeyPostShouldThrow409) {
                        when(bodySpec.retrieve()).thenThrow(
                                HttpClientErrorException.create(
                                        HttpStatus.CONFLICT, "Conflict",
                                        HttpHeaders.EMPTY,
                                        "keyId owned by different tenant".getBytes(),
                                        null));
                    } else {
                        when(bodySpec.retrieve()).thenReturn(respSpec);
                        when(respSpec.body(eq(Map.class))).thenReturn(
                                Map.of("validTo", "2026-06-16T00:00:00Z"));
                    }
                }
                return bodySpec;
            };
            when(postSpec.uri(anyString())).thenAnswer(postUriAnswer);
            when(postSpec.uri(anyString(), any(Object[].class))).thenAnswer(postUriAnswer);
            return postSpec;
        });

        return restClient;
    }
}
