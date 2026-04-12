package com.java_template.common.workflow;

// ABOUTME: Framework hook for authorizing CRUD and transition access on entities at the API layer.
// Implement this interface as a Spring bean to enforce access control on EntityCrudController.

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface EntityAccessAuthorizer {
    /**
     * Authorizes a CRUD or transition request.
     * Throw AccessDeniedException if the current principal is not permitted.
     *
     * @param access     the operation type (CREATE, READ, UPDATE, DELETE)
     * @param entityType entity type name (e.g. "Offer")
     * @param version    entity schema version
     * @param entityId   UUID of the target entity; null for CREATE (no entity yet)
     * @param transition workflow transition name; non-null only for UPDATE transitions
     */
    void authorize(AccessRequest access, String entityType, int version,
                   @Nullable UUID entityId, @Nullable String transition);
}
