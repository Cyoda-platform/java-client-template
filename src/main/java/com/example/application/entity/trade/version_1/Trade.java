package com.example.application.entity.trade.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Trade entity representing an executed trade with settlement details
 */
@Data
public class Trade implements CyodaEntity {
    public static final String ENTITY_NAME = "Trade";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String tradeId;

    // Core fields
    private String orderId;
    private String accountId;
    private String symbol;
    private String side; // BUY, SELL
    private Integer quantity;
    private Double executionPrice;
    private Double totalValue;
    private String status; // CONFIRMED, SETTLED, FAILED

    // Settlement details
    private String settlementStatus; // PENDING, SETTLED, FAILED
    private LocalDateTime settlementDate;
    private String counterparty;

    // P&L tracking
    private Double realizedPnL;
    private Double commission;
    private Double fees;

    // Timestamps
    private LocalDateTime executedAt;
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
        return tradeId != null && !tradeId.isBlank() &&
               orderId != null && !orderId.isBlank() &&
               quantity != null && quantity > 0 &&
               executionPrice != null && executionPrice > 0;
    }
}

