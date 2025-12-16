package com.java_template.application.entity.portfolio.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Portfolio Entity - Represents a trading portfolio/account
 * Tracks cash balance, positions, and portfolio-level metrics
 */
@Data
public class Portfolio implements CyodaEntity {
    public static final String ENTITY_NAME = Portfolio.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique portfolio ID
    private String portfolioId;

    // Owner/Account ID
    private String ownerId;

    // Portfolio name
    private String name;

    // Portfolio description
    private String description;

    // Cash balance in base currency
    private Double cashBalance;

    // Total portfolio value (cash + positions market value)
    private Double totalValue;

    // Total unrealized P&L
    private Double unrealizedPnl;

    // Total realized P&L
    private Double realizedPnl;

    // Base currency
    private String currency;

    // Portfolio status: ACTIVE, SUSPENDED, CLOSED
    private String status;

    // List of position IDs in this portfolio
    private List<String> positionIds;

    // Portfolio creation timestamp
    private LocalDateTime createdAt;

    // Portfolio last updated timestamp
    private LocalDateTime updatedAt;

    // Last mark-to-market timestamp
    private LocalDateTime lastMarkToMarketTime;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return portfolioId != null && !portfolioId.trim().isEmpty() &&
               ownerId != null && !ownerId.trim().isEmpty() &&
               cashBalance != null &&
               currency != null && !currency.trim().isEmpty();
    }
}

