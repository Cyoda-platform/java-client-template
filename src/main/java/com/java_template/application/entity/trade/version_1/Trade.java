package com.java_template.application.entity.trade.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Trade Entity - Represents an executed trade (fill) in the system
 * Immutable record of actual execution
 */
@Data
public class Trade implements CyodaEntity {
    public static final String ENTITY_NAME = "Trade";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifiers
    private String tradeId;
    private String orderId;
    private String accountId;
    private String executionId;

    // Trade details
    private String symbol;
    private String assetType; // EQUITY, OPTION, FUTURE
    private String side; // BUY, SELL
    private Double quantity;
    private Double price;
    private Double commission;
    private Double totalValue;

    // Settlement
    private String settlementStatus; // PENDING, SETTLED, FAILED
    private LocalDateTime settlementDate;
    private String counterparty;

    // Audit trail
    private String venue; // Exchange or broker
    private String executionVenue;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
    private String createdBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return tradeId != null && !tradeId.isBlank() &&
               orderId != null && !orderId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               quantity != null && quantity > 0 &&
               price != null && price > 0;
    }
}

