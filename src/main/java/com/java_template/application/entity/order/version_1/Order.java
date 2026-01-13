package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order Entity - Represents a trading order in the Order Management System
 * 
 * Supports limit and market orders with full lifecycle management.
 * States: NEW, VALIDATED, ROUTED, ACKNOWLEDGED, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String orderId;

    // Core order fields
    private String symbol;
    private String side; // BUY or SELL
    private Long quantity;
    private Double price; // Optional for market orders
    private String orderType; // LIMIT or MARKET
    private String timeInForce; // GTC, IOC, FOK, etc.
    private String traderId;
    private LocalDateTime timestamp;

    // Order tracking
    private Long filledQuantity;
    private Long remainingQuantity;
    private Double executedPrice;
    private String status; // Mirrors workflow state

    // Audit fields
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
        // Validate required fields
        return orderId != null && !orderId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               side != null && (side.equals("BUY") || side.equals("SELL")) &&
               quantity != null && quantity > 0 &&
               orderType != null && (orderType.equals("LIMIT") || orderType.equals("MARKET")) &&
               traderId != null && !traderId.isBlank();
    }
}

