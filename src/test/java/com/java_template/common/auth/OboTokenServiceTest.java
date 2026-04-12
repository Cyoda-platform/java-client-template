package com.java_template.common.auth;

import com.java_template.common.config.Config;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ABOUTME: Verifies OboTokenService error handling for specific HTTP error responses
 * from the Cyoda token exchange endpoint (403 tenant mismatch, 401 invalid subject token).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OboTokenServiceTest {

    @Mock private SubjectTokenSigner subjectTokenSigner;
    @Mock private Config config;

    private OboTokenService service;
    private HttpClientErrorException errorToThrow;

    @BeforeEach
    void setUp() throws Exception {
        OboProperties oboProperties = new OboProperties();
        oboProperties.setEncryptionKey("BO1DMziwLVFBy+eBH4KSewhkCaPpP+2XTw018eTacho=");

        when(config.getCyodaApiUrl()).thenReturn("http://cyoda.test/api");
        when(config.getCyodaClientId()).thenReturn("client-id");
        when(config.getCyodaClientSecret()).thenReturn("client-secret");
        when(config.getTrustedHosts()).thenReturn(List.of());

        when(subjectTokenSigner.sign(anyString(), any())).thenReturn("signed-subject-token");

        service = new OboTokenService(oboProperties, subjectTokenSigner, config);

        RestClient mockRestClient = buildMockRestClient();
        Field restClientField = OboTokenService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(service, mockRestClient);
    }

    @Test
    void exchange_403_throwsTenantViolation() {
        errorToThrow = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY,
                "{\"error\":\"access_denied\",\"error_description\":\"Tenant boundary violation\"}".getBytes(),
                null);

        assertThatThrownBy(() -> service.getOboToken("user-1", List.of("INVESTOR")))
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("tenant boundary violation")
                .hasMessageContaining("user-1");
    }

    @Test
    void exchange_401_throwsSigningKeyRotation() {
        errorToThrow = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Subject token validation failed\"}".getBytes(),
                null);

        assertThatThrownBy(() -> service.getOboToken("user-1", List.of("INVESTOR")))
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("signing key may need rotation")
                .hasMessageContaining("user-1");
    }

    private RestClient buildMockRestClient() {
        RestClient restClient = mock(RestClient.class);

        when(restClient.post()).thenAnswer(inv -> {
            RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
            when(postSpec.uri(anyString())).thenAnswer(uriInv -> {
                RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
                when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
                when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
                when(bodySpec.body(any(String.class))).thenReturn(bodySpec);
                when(bodySpec.retrieve()).thenThrow(errorToThrow);
                return bodySpec;
            });
            return postSpec;
        });

        return restClient;
    }
}
