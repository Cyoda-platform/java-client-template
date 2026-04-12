package com.java_template.common.workflow;

// ABOUTME: Default EntityAccessAuthorizer that permits all operations unconditionally.
// Active only when no application-supplied EntityAccessAuthorizer bean is present.

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(EntityAccessAuthorizer.class)
public class AllowAllEntityAccessAuthorizer implements EntityAccessAuthorizer {
    @Override
    public void authorize(AccessRequest access, String entityType, int version,
                          @Nullable UUID entityId, @Nullable String transition) {
        // permit all by default
    }
}
