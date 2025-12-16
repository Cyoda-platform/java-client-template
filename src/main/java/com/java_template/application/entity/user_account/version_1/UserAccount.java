package com.java_template.application.entity.user_account.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UserAccount Entity - Represents user accounts with RBAC
 * Manages authentication, authorization, and account status
 */
@Data
public class UserAccount implements CyodaEntity {
    public static final String ENTITY_NAME = UserAccount.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique user ID
    private String userId;

    // User name
    private String name;

    // Email address
    private String email;

    // Phone number
    private String phone;

    // User role: ADMIN, TRADER, RISK_MANAGER, COMPLIANCE, VIEWER
    private String role;

    // List of permissions
    private List<String> permissions;

    // Account status: ACTIVE, SUSPENDED, LOCKED, INACTIVE
    private String accountStatus;

    // Last login timestamp
    private LocalDateTime lastLoginTime;

    // Account creation timestamp
    private LocalDateTime createdAt;

    // Account last updated timestamp
    private LocalDateTime updatedAt;

    // Password last changed timestamp
    private LocalDateTime passwordChangedAt;

    // Failed login attempts
    private Integer failedLoginAttempts;

    // Account locked until timestamp
    private LocalDateTime lockedUntil;

    // Two-factor authentication enabled
    private Boolean twoFactorEnabled;

    // Department/Team
    private String department;

    // Manager ID
    private String managerId;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return userId != null && !userId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               role != null && !role.trim().isEmpty();
    }
}

