package com.java_template.common.auth;

import io.cloudevents.v1.proto.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Establishes the correct SecurityContext for a CloudEvent processing thread based on the
 * CloudEvents Auth Context Extension. Delegates user resolution and verification to the
 * injected {@link EventUserResolver} bean, then installs a synthetic {@link JwtAuthenticationToken}
 * so that {@code OboAwareAuthentication} performs OBO for all downstream Cyoda calls (gRPC and HTTP).
 */
@Service
@EnableConfigurationProperties(EventAuthContextProperties.class)
public class EventAuthContextHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventAuthContextHandler.class);

    private final EventAuthContextProperties properties;
    private final CloudEventAuthContextExtractor extractor;
    private final EventUserResolver userResolver;
    private final OboProperties oboProperties;

    public EventAuthContextHandler(
            EventAuthContextProperties properties,
            CloudEventAuthContextExtractor extractor,
            EventUserResolver userResolver,
            OboProperties oboProperties
    ) {
        this.properties = properties;
        this.extractor = extractor;
        this.userResolver = userResolver;
        this.oboProperties = oboProperties;
    }

    /**
     * Establishes the SecurityContext appropriate for this CloudEvent and returns a scope
     * that restores the previous context on close. Use in try-with-resources.
     *
     * @throws EventAuthContextMissingException if auth context is absent and mode is REQUIRED
     * @throws EventUserResolutionException     if authtype=user but resolver fails
     */
    public EventAuthContextScope establish(CloudEvent cloudEvent) {
        SecurityContext previous = SecurityContextHolder.getContext();

        if (properties.getMode() == AuthContextMode.IGNORE) {
            SecurityContextHolder.clearContext();
            return new EventAuthContextScope(previous);
        }

        Optional<CloudEventAuthContext> maybeCtx = extractor.extract(cloudEvent);

        if (maybeCtx.isEmpty()) {
            handleAbsent();
            return new EventAuthContextScope(previous);
        }

        handlePresent(maybeCtx.get());
        return new EventAuthContextScope(previous);
    }

    private void handleAbsent() {
        switch (properties.getMode()) {
            case REQUIRED -> throw new EventAuthContextMissingException(
                    "CloudEvent auth context is required (mode=REQUIRED) but absent");
            case OPTIONAL -> {
                logger.warn("CloudEvent carries no auth context — falling back to M2M (mode=OPTIONAL)");
                SecurityContextHolder.clearContext();
            }
            default -> SecurityContextHolder.clearContext();
        }
    }

    private void handlePresent(CloudEventAuthContext ctx) {
        if (!ctx.isUserContext()) {
            logger.debug("CloudEvent auth context authtype='{}' — using M2M", ctx.authType());
            SecurityContextHolder.clearContext();
            return;
        }

        // Explicitly clear context before calling the resolver so that any Cyoda lookups
        // inside resolve() use the M2M service account.
        SecurityContextHolder.clearContext();

        logger.debug("CloudEvent auth context authtype='user', authid='{}' — resolving user", ctx.authId());
        EventUserIdentity identity = userResolver.resolve(ctx);

        SecurityContextHolder.setContext(buildSecurityContext(identity));
        logger.debug("Switched SecurityContext to user '{}' for event processing", identity.userId());
    }

    private SecurityContext buildSecurityContext(EventUserIdentity identity) {
        // Build a synthetic Jwt recognised by OboAwareAuthentication.
        // - "sub" claim carries the user UUID used by OboTokenService as the cache key and subject token sub
        // - The roles claim name matches oboProperties.getRolesClaimName() (what OboAwareAuthentication reads)
        // The token value is a non-secret placeholder; it is never validated as a real JWT.
        Jwt jwt = Jwt.withTokenValue("event-auth-context-synthetic")
                .header("alg", "none")
                .subject(identity.userId())
                .claim(oboProperties.getRolesClaimName(), identity.roles())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        return ctx;
    }
}
