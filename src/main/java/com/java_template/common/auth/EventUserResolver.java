package com.java_template.common.auth;

/**
 * Extension point for resolving and verifying user identity from a CloudEvent auth context.
 * Implementations are provided by the application layer and may enforce user existence,
 * role checks, and permission validation appropriate to the domain.
 *
 * <p>The {@code SecurityContextHolder} is empty when {@code resolve()} is called — any
 * Cyoda calls inside the implementation use the M2M service account automatically.
 */
public interface EventUserResolver {

    /**
     * Resolves and verifies the user identity described by the auth context.
     * Only called when {@code authContext.isUserContext()} is {@code true}.
     *
     * @param authContext the CloudEvent auth context
     * @return resolved identity (userId + roles)
     * @throws EventUserResolutionException if the user cannot be found, is not authorised,
     *                                      or any application-level check fails
     */
    EventUserIdentity resolve(CloudEventAuthContext authContext);
}
