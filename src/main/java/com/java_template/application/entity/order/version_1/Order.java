package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order Entity - Represents trading orders in the system
 * Supports order lifecycle: NEW -> ACK -> ROUTED -> FILLED/PARTIAL_FILL -> CANCELLED/REJECTED
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique order ID
    private String orderId;

    // Portfolio/Account ID that placed the order
    private String portfolioId;

    // Instrument symbol
    private String instrumentSymbol;

    // Order side: BUY or SELL
    private String side;

    // Order type: MARKET, LIMIT, STOP, STOP_LIMIT
    private String orderType;

    // Quantity ordered
    private Long quantity;

    // Limit price (for limit orders)
    private Double limitPrice;

    // Stop price (for stop orders)
    private Double stopPrice;

    // Time in force: DAY, GTC (Good Till Cancel), IOC (Immediate or Cancel), FOK (Fill or Kill)
    private String timeInForce;

    // Quantity filled so far
    private Long quantityFilled;

    // Average execution price
    private Double averageExecutionPrice;

    // Order creation timestamp
    private LocalDateTime createdAt;

    // Order last updated timestamp
    private LocalDateTime updatedAt;

    // Order expiration time (for GTC orders)
    private LocalDateTime expirationTime;

    // User/Account ID that placed the order
    private String userId;

    // Order routing destination (exchange, internal matching, etc.)
    private String routingDestination;

    // External order ID from exchange
    private String externalOrderId;

    // Rejection reason (if order was rejected)
    private String rejectionReason;

    // Cancellation reason (if order was cancelled)
    private String cancellationReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return orderId != null && !orderId.trim().isEmpty() &&
               portfolioId != null && !portfolioId.trim().isEmpty() &&
               instrumentSymbol != null && !instrumentSymbol.trim().isEmpty() &&
               side != null && !side.trim().isEmpty() &&
               quantity != null && quantity > 0;
    }
}

