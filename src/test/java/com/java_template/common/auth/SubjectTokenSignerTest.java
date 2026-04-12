package com.java_template.common.auth;

// ABOUTME: Unit tests for SubjectTokenSigner RS256 JWT generation and key lifecycle.

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTokenSignerTest {

    private SubjectTokenSigner signer;

    @BeforeEach
    void setUp() {
        OboProperties props = new OboProperties();
        props.setKeyId("test-key");
        props.setIssuer("test-issuer");
        props.setSubjectTokenTtlSeconds(300);
        signer = new SubjectTokenSigner(props);
    }

    @Test
    void sign_throwsOboTokenException_whenKeyNotLoaded() {
        assertThatThrownBy(() -> signer.sign("user-1", List.of()))
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("not yet available");
    }

    @Test
    void sign_throwsOboTokenException_whenCaasOrgIdNotLoaded() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        signer.setActiveKey((RSAPrivateKey) kpg.generateKeyPair().getPrivate());
        // activeCaasOrgId not set
        assertThatThrownBy(() -> signer.sign("user-1", List.of()))
                .isInstanceOf(OboTokenException.class)
                .hasMessageContaining("caas_org_id");
    }

    @Test
    void sign_producesValidRs256JwtWithAllRequiredClaims() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        RSAPrivateKey key = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();
        signer.setActiveKey(key);
        signer.setActiveCaasOrgId("test-org");

        String token = signer.sign("user-uuid-1", List.of("INVESTOR", "ADMIN"));

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("test-key");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("user-uuid-1");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("test-issuer");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("user_roles"))
                .containsExactly("INVESTOR", "ADMIN");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("caas_org_id")).isEqualTo("test-org");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new Date());
    }

    @Test
    void isKeyLoaded_returnsFalse_beforeKeySet() {
        assertThat(signer.isKeyLoaded()).isFalse();
    }

    @Test
    void isKeyLoaded_returnsTrue_afterKeySet() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        RSAPrivateKey key = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();
        signer.setActiveKey(key);
        assertThat(signer.isKeyLoaded()).isTrue();
    }

    @Test
    void setActiveKey_updatesKeyAtomically() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        RSAPrivateKey key1 = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();
        RSAPrivateKey key2 = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();

        signer.setActiveKey(key1);
        signer.setActiveCaasOrgId("test-org");
        assertThat(signer.isKeyLoaded()).isTrue();

        signer.setActiveKey(key2);
        assertThatCode(() -> signer.sign("user-1", List.of())).doesNotThrowAnyException();
    }
}
