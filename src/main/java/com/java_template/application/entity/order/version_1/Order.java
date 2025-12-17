package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Order Entity - Represents a trading order (limit or market)
 * Tracks order lifecycle from placement to settlement
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String orderId;

    // References
    private String accountId;
    private String marketId;

    // Order details
    private String side; // "BUY" or "SELL"
    private String type; // "LIMIT" or "MARKET"
    private BigDecimal price; // For limit orders
    private BigDecimal quantity;
    private BigDecimal filled; // Amount filled so far
    private BigDecimal remaining; // Quantity remaining

    // Time in force
    private String timeInForce; // "GTC" (Good-Till-Cancel), "IOC" (Immediate-Or-Cancel), "FOK" (Fill-Or-Kill)

    // Order status
    private String status; // "NEW", "PLACED", "PARTIALLY_FILLED", "FILLED", "CANCELLED", "REJECTED"

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime filledAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return orderId != null && !orderId.isBlank() &&
               accountId != null && !accountId.isBlank() &&
               marketId != null && !marketId.isBlank() &&
               quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}

