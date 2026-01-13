package com.example.application.entity.portfolio.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Portfolio entity representing aggregated holdings and P&L for an account
 */
@Data
public class Portfolio implements CyodaEntity {
    public static final String ENTITY_NAME = "Portfolio";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String portfolioId;

    // Core fields
    private String accountId;
    private String status; // ACTIVE, CLOSED

    // Valuation
    private Double totalMarketValue;
    private Double totalCost;
    private Double realizedPnL;
    private Double unrealizedPnL;
    private Double totalPnL;
    private Double pnLPercent;

    // Risk metrics
    private Double totalExposure;
    private Double var95; // Value at Risk 95%
    private Double maxDrawdown;

    // Position tracking
    private Integer totalPositions;
    private List<String> positionIds;

    // Timestamps
    private LocalDateTime createdAt;
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
        return portfolioId != null && !portfolioId.isBlank() &&
               accountId != null && !accountId.isBlank();
    }
}

