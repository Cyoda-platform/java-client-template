package com.java_template.common.auth;

// ABOUTME: Signs RS256 subject tokens for Cyoda OBO token exchange (RFC 8693).
// Receives pre-processed role strings (no "ROLE_" prefix) and places them in the user_roles claim.
// The signing key is injected by OboKeyRegistrationService after decryption from the OboSigningKey entity.

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class SubjectTokenSigner {

    private static final Logger logger = LoggerFactory.getLogger(SubjectTokenSigner.class);

    private final OboProperties oboProperties;

    private volatile RSAPrivateKey activeKey;
    private volatile String activeCaasOrgId;

    public SubjectTokenSigner(OboProperties oboProperties) {
        this.oboProperties = oboProperties;
    }

    public String sign(String userId, List<String> userRoles) {
        RSAPrivateKey key = activeKey;
        String orgId = activeCaasOrgId;
        if (key == null) {
            throw new OboTokenException(
                    "OBO signing key is not yet available. OboKeyRegistrationService may have " +
                    "failed to load the key from Cyoda at startup. Check logs for details.");
        }
        if (orgId == null) {
            throw new OboTokenException(
                    "caas_org_id is not yet available. OboKeyRegistrationService may have " +
                    "failed to fetch it from the Cyoda /account endpoint at startup. Check logs.");
        }
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(oboProperties.getKeyId())
                    .build();

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(oboProperties.getIssuer())
                    .subject(userId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(oboProperties.getSubjectTokenTtlSeconds())))
                    .claim("caas_org_id", orgId)
                    .claim("user_roles", userRoles)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();

        } catch (OboTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OboTokenException("Failed to sign OBO subject token for user: " + userId, e);
        }
    }

    public void setActiveKey(RSAPrivateKey key) {
        this.activeKey = key;
        logger.info("OBO signing key activated (key ID: {})", oboProperties.getKeyId());
    }

    public void setActiveCaasOrgId(String caasOrgId) {
        this.activeCaasOrgId = caasOrgId;
        logger.info("OBO caas_org_id set ({})", caasOrgId);
    }

    public boolean isKeyLoaded() {
        return activeKey != null;
    }
}
