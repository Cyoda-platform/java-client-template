package com.example.application.entity.account.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Account entity representing an institutional trading account
 * with balance, status, and risk limits
 */
@Data
public class Account implements CyodaEntity {
    public static final String ENTITY_NAME = "Account";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String accountNumber;

    // Core fields
    private String accountType; // INSTITUTIONAL, RETAIL, PROPRIETARY
    private String status; // ACTIVE, SUSPENDED, CLOSED
    private Double balance;
    private String currency; // USD, EUR, etc.

    // Risk limits
    private Double dailyLossLimit;
    private Double positionLimit;
    private Double orderValueLimit;

    // Metadata
    private String description;
    private String region;
    private String tier; // PREMIUM, STANDARD, BASIC

    // Timestamps
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
        return accountNumber != null && !accountNumber.isBlank() &&
               balance != null && balance >= 0;
    }
}

