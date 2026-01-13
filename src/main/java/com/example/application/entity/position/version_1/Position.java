package com.example.application.entity.position.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Position entity representing a current holding in a security
 */
@Data
public class Position implements CyodaEntity {
    public static final String ENTITY_NAME = "Position";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String positionId;

    // Core fields
    private String accountId;
    private String symbol;
    private Integer quantity;
    private Double averageCost;
    private Double currentPrice;
    private String status; // OPEN, CLOSED

    // Valuation
    private Double marketValue;
    private Double unrealizedPnL;
    private Double unrealizedPnLPercent;

    // Risk metrics
    private Double exposure;
    private Double weightInPortfolio;

    // Timestamps
    private LocalDateTime openedAt;
    private LocalDateTime lastUpdatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return positionId != null && !positionId.isBlank() &&
               accountId != null && !accountId.isBlank() &&
               symbol != null && !symbol.isBlank() &&
               quantity != null;
    }
}

