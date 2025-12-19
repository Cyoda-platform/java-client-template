package com.java_template.application.entity.account.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Account Entity - Represents a trading account
 * Holds account-level settings, balances, and limits
 */
@Data
public class Account implements CyodaEntity {
    public static final String ENTITY_NAME = "Account";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String accountId;
    private String accountName;
    private String accountType; // INDIVIDUAL, INSTITUTIONAL, PROP
    private String status; // ACTIVE, SUSPENDED, CLOSED

    // Financial details
    private Double cashBalance;
    private Double totalEquity;
    private Double buyingPower;
    private Double maintenanceMargin;
    private Double initialMargin;
    private Double marginUtilization;

    // Account settings
    private String currency;
    private List<String> allowedAssetTypes; // EQUITY, OPTION, FUTURE
    private Boolean marginEnabled;
    private Boolean shortSellEnabled;
    private Boolean optionsEnabled;

    // Compliance
    private String complianceStatus; // COMPLIANT, WARNING, VIOLATION
    private String regulatoryStatus;
    private LocalDateTime lastComplianceCheckAt;

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
        return accountId != null && !accountId.isBlank() &&
               accountName != null && !accountName.isBlank() &&
               totalEquity != null && totalEquity >= 0;
    }
}

