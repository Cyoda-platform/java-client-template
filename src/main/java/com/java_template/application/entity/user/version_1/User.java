package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Entity - Represents a user in the crypto exchange platform
 * Tracks user identity, roles, and account status
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String userId;

    // Core user information
    private String email;
    private String name;
    private String phone;

    // User roles and permissions
    private List<String> roles; // e.g., ["TRADER", "ADMIN", "COMPLIANCE_OFFICER"]

    // User status
    private String status; // e.g., "ACTIVE", "SUSPENDED", "INACTIVE", "PENDING_KYC"

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    // Optional: KYC status reference
    private String kycProfileId;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return userId != null && !userId.isBlank() &&
               email != null && !email.isBlank() &&
               name != null && !name.isBlank();
    }
}

