package com.example.application.entity.account.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Account Entity for Compliance Management Platform
 * 
 * Represents a financial account with balance tracking,
 * account type classification, and compliance status.
 */
@Data
public class Account implements CyodaEntity {
    public static final String ENTITY_NAME = "Account";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Core account information
    private String customerId;
    private String accountNumber;
    private AccountType accountType;
    private String currency;
    private Status status;

    // Balance information
    private Balance balances;

    // Audit and metadata fields
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Map<String, Object> metadata;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required business identifiers
        return id != null && !id.isBlank() && customerId != null && !customerId.isBlank();
    }

    /**
     * Nested class for balance information
     */
    @Data
    public static class Balance {
        private BigDecimal available;
        private BigDecimal ledger;
        private LocalDateTime lastUpdated;
    }

    /**
     * Account type enumeration
     */
    public enum AccountType {
        CHECKING,
        SAVINGS,
        WALLET
    }

    /**
     * Account status enumeration
     */
    public enum Status {
        ACTIVE,
        INACTIVE,
        CLOSED,
        SUSPENDED
    }
}

