package com.java_template.application.entity.trade.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Trade Entity - Represents executed trades/fills
 * Immutable record of trade execution with settlement details
 */
@Data
public class Trade implements CyodaEntity {
    public static final String ENTITY_NAME = Trade.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique trade ID
    private String tradeId;

    // Reference to the order that generated this trade
    private String orderId;

    // Portfolio/Account ID
    private String portfolioId;

    // Instrument symbol
    private String instrumentSymbol;

    // Trade side: BUY or SELL
    private String side;

    // Execution quantity
    private Long quantity;

    // Execution price
    private Double executionPrice;

    // Trade value (quantity * executionPrice)
    private Double tradeValue;

    // Commission/fees
    private Double commission;

    // Net trade value (tradeValue - commission)
    private Double netValue;

    // Execution timestamp
    private LocalDateTime executionTime;

    // Settlement date
    private LocalDateTime settlementDate;

    // Counterparty (exchange, broker, etc.)
    private String counterparty;

    // External trade ID from exchange
    private String externalTradeId;

    // Settlement status: PENDING, SETTLED, FAILED
    private String settlementStatus;

    // Settlement instruction ID
    private String settlementInstructionId;

    // Trade creation timestamp
    private LocalDateTime createdAt;

    // Trade last updated timestamp
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
        return tradeId != null && !tradeId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               portfolioId != null && !portfolioId.trim().isEmpty() &&
               instrumentSymbol != null && !instrumentSymbol.trim().isEmpty() &&
               quantity != null && quantity > 0 &&
               executionPrice != null && executionPrice > 0;
    }
}

