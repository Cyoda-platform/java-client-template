package com.java_template.application.entity.trade.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Trade Entity - Represents a matched trade between two orders
 * Records execution details and settlement information
 */
@Data
public class Trade implements CyodaEntity {
    public static final String ENTITY_NAME = "Trade";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String tradeId;

    // Order references
    private String buyOrderId;
    private String sellOrderId;

    // Market reference
    private String marketId;

    // Trade details
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal totalValue; // price * quantity

    // Fee information
    private BigDecimal buyerFee;
    private BigDecimal sellerFee;

    // Trade status
    private String status; // "MATCHED", "SETTLED", "FAILED"

    // Metadata
    private LocalDateTime timestamp;
    private LocalDateTime settledAt;

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
               buyOrderId != null && !buyOrderId.isBlank() &&
               sellOrderId != null && !sellOrderId.isBlank() &&
               price != null && price.compareTo(BigDecimal.ZERO) > 0 &&
               quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}

