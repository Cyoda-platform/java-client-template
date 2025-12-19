package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Represents a trading order in the system
 * Supports equities, options, and futures with various order types
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String orderId;
    private String accountId;
    private String clientOrderId;

    // Order details
    private String symbol;
    private String assetType; // EQUITY, OPTION, FUTURE
    private String orderType; // MARKET, LIMIT, STOP, IOC, FOK
    private String side; // BUY, SELL
    private Double quantity;
    private Double price;
    private Double stopPrice;
    private String timeInForce; // DAY, GTC, IOC, FOK

    // Order state tracking
    private String status; // NEW, ACK, PARTIAL_FILL, FILLED, CANCELLED, REJECTED
    private Double filledQuantity;
    private Double averagePrice;
    private List<String> fills;

    // Risk and compliance
    private Double estimatedCost;
    private String riskCheckStatus; // PENDING, PASSED, FAILED
    private String complianceStatus; // PENDING, APPROVED, REJECTED

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime executedAt;

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

