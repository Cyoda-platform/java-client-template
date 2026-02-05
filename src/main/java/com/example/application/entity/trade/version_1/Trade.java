package com.example.application.entity.trade.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;

/**
 * Trade Entity - Represents a financial trade transaction
 * Implements CyodaEntity for workflow integration
 */
@Data
public class Trade implements CyodaEntity {
    public static final String ENTITY_NAME = Trade.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String id;

    // Core trade fields
    private String tradeDate;
    private String instrumentId;
    private BigDecimal quantity;
    private BigDecimal price;
    private TradeType tradeType;
    private String counterpartyId;
    private String currency;
    private TradeStatus status;
    private String source;
    private String createdAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return id != null && !id.isBlank() &&
               instrumentId != null && !instrumentId.isBlank() &&
               quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0 &&
               price != null && price.compareTo(BigDecimal.ZERO) > 0 &&
               tradeType != null &&
               counterpartyId != null && !counterpartyId.isBlank() &&
               currency != null && !currency.isBlank();
    }

    /**
     * Enum for trade type
     */
    public enum TradeType {
        BUY, SELL
    }

    /**
     * Enum for trade status
     */
    public enum TradeStatus {
        RECEIVED, VALIDATED, REJECTED, ROUTED, COMPLETED
    }
}

