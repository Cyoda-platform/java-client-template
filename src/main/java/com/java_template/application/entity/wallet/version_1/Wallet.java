package com.java_template.application.entity.wallet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Wallet Entity - Represents a multi-asset wallet for an account
 * Supports custody separation (hot/cold/custodial)
 */
@Data
public class Wallet implements CyodaEntity {
    public static final String ENTITY_NAME = "Wallet";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String walletId;

    // References
    private String accountId;
    private String assetId;

    // Wallet type (custody separation)
    private String type; // e.g., "HOT", "COLD", "CUSTODIAL"

    // Balance information
    private BigDecimal balance; // Available balance
    private BigDecimal reserved; // Reserved for pending orders/withdrawals
    private BigDecimal total; // Total = balance + reserved

    // Wallet status
    private String status; // e.g., "ACTIVE", "SUSPENDED", "LOCKED"

    // On-chain information (for crypto)
    private String address; // Wallet address
    private String publicKey; // Public key (if applicable)

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return walletId != null && !walletId.isBlank() &&
               accountId != null && !accountId.isBlank() &&
               assetId != null && !assetId.isBlank() &&
               balance != null && balance.compareTo(BigDecimal.ZERO) >= 0;
    }
}

