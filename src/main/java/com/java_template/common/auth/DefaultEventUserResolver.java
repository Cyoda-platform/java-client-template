package com.java_template.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link EventUserResolver} used in java_template environments where no application-specific
 * resolver is registered. Trusts authid and authclaims without any database verification.
 * In production, this bean is replaced by an application-layer implementation.
 */
@Component
@ConditionalOnMissingBean(value = EventUserResolver.class, ignored = DefaultEventUserResolver.class)
public class DefaultEventUserResolver implements EventUserResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEventUserResolver.class);

    private final ObjectMapper objectMapper;

    public DefaultEventUserResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EventUserIdentity resolve(CloudEventAuthContext authContext) {
        String authId = authContext.authId();
        if (authId == null || authId.isBlank()) {
            throw new EventUserResolutionException(
                    "Cannot resolve user: authtype=user but authid attribute is absent");
        }
        logger.warn("DefaultEventUserResolver: trusting authid='{}' without user verification " +
                "(no application EventUserResolver bean is registered)", authId);
        List<String> roles = AuthClaimsParser.parseRoles(objectMapper, authContext.authClaimsJson());
        return new EventUserIdentity(authId, roles);
    }
}
