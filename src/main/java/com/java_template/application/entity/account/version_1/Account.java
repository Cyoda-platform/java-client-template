package com.java_template.application.entity.account.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Account Entity - Represents a trading account for a user
 * Links users to their trading capabilities and wallet management
 */
@Data
public class Account implements CyodaEntity {
    public static final String ENTITY_NAME = "Account";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String accountId;

    // Reference to user
    private String userId;

    // Account type
    private String type; // e.g., "INDIVIDUAL", "INSTITUTIONAL", "CUSTODIAL"

    // Account status
    private String status; // e.g., "ACTIVE", "SUSPENDED", "CLOSED", "PENDING_VERIFICATION"

    // Account tier/level
    private String tier; // e.g., "BASIC", "PREMIUM", "VIP"

    // Trading limits
    private Double dailyWithdrawalLimit;
    private Double dailyTradeLimit;
    private Double monthlyWithdrawalLimit;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return accountId != null && !accountId.isBlank() &&
               userId != null && !userId.isBlank() &&
               type != null && !type.isBlank();
    }
}

