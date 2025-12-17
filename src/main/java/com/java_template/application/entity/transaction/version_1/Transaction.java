package com.java_template.application.entity.transaction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Transaction Entity - Represents a ledger entry for balance-changing operations
 * Immutable record of deposits, withdrawals, transfers, and settlements
 */
@Data
public class Transaction implements CyodaEntity {
    public static final String ENTITY_NAME = "Transaction";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String transactionId;

    // References
    private String walletId;
    private String assetId;

    // Transaction type
    private String type; // "DEPOSIT", "WITHDRAWAL", "TRANSFER", "SETTLEMENT", "FEE"

    // Amount
    private BigDecimal amount;

    // Transaction status
    private String status; // "PENDING", "CONFIRMED", "FAILED", "CANCELLED"

    // Ledger reference
    private String ledgerEntryId; // Reference to immutable ledger

    // On-chain information (for crypto)
    private String txHash; // Transaction hash
    private Integer confirmations; // Number of confirmations

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return transactionId != null && !transactionId.isBlank() &&
               walletId != null && !walletId.isBlank() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}

