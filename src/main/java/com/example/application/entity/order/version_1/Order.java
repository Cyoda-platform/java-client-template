package com.example.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order entity representing a trading order with side, type, quantity, and execution details
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String orderId;

    // Core fields
    private String accountId;
    private String symbol;
    private String side; // BUY, SELL
    private String type; // MARKET, LIMIT, STOP, STOP_LIMIT, ICEBERG
    private Integer quantity;
    private Double price;
    private String status; // PENDING, ACCEPTED, FILLED, PARTIAL_FILL, CANCELLED, REJECTED

    // Execution details
    private Integer filledQuantity;
    private Double averageFilledPrice;
    private String executionVenue;

    // Risk and validation
    private Double estimatedValue;
    private String validationStatus; // PASSED, FAILED

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

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
               symbol != null && !symbol.isBlank() &&
               quantity != null && quantity > 0;
    }
}

