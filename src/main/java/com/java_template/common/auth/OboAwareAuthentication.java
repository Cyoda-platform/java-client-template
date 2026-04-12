package com.java_template.common.auth;

// ABOUTME: OBO-aware token provider that enforces user identity propagation to Cyoda.
// When a user JWT is in the SecurityContext, OBO is mandatory and fails loudly on error.
// M2M is used only when the SecurityContext holds no user (infrastructure/background paths).

import com.java_template.common.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@EnableConfigurationProperties(OboProperties.class)
public class OboAwareAuthentication extends Authentication {

    private static final Logger logger = LoggerFactory.getLogger(OboAwareAuthentication.class);

    private final OboProperties oboProperties;
    private final OboTokenService oboTokenService;

    public OboAwareAuthentication(Config config, OboProperties oboProperties, OboTokenService oboTokenService) {
        super(config);
        this.oboProperties = oboProperties;
        this.oboTokenService = oboTokenService;
    }

    @Override
    public OAuth2AccessToken getAccessToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return getOboTokenOrThrow(jwt);
        }
        logger.trace("No user context on current thread — using M2M service account token");
        return super.getAccessToken();
    }

    private OAuth2AccessToken getOboTokenOrThrow(JwtAuthenticationToken jwt) {
        if (!oboProperties.isEnabled()) {
            throw new OboTokenException(
                    "OBO token exchange is not configured. "
                            + "No encryption key found via app.obo.encryption-key property or "
                            + "app.obo.encryption-key-file. Cannot execute a user-attributed Cyoda call.");
        }

        String sub = jwt.getToken().getClaimAsString("sub");
        if (sub == null || sub.isBlank()) {
            throw new OboTokenException("Cannot perform OBO exchange: JWT has no 'sub' claim");
        }

        List<String> rawRoles = jwt.getToken().getClaimAsStringList(oboProperties.getRolesClaimName());
        List<String> userRoles = stripRolePrefix(rawRoles);

        return oboTokenService.getOboToken(sub, userRoles)
                .orElseThrow(() -> new OboTokenException(
                        "OBO token exchange failed for user " + sub +
                        ". See logs for exchange error details."));
    }

    private static List<String> stripRolePrefix(List<String> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();
    }
}
