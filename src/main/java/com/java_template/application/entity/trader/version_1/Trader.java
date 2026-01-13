package com.java_template.application.entity.trader.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Trader Entity - Represents a trader in the system
 * 
 * Stores trader information and risk limits for order validation.
 */
@Data
public class Trader implements CyodaEntity {
    public static final String ENTITY_NAME = "Trader";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String traderId;

    // Trader information
    private String name;
    private String email;
    private String department;

    // Risk limits
    private Long maxOrderSize; // Maximum quantity per order
    private Double maxNotionalPerDay; // Maximum notional value per day
    private Double currentDayNotional; // Current day's notional value

    // Status
    private String status; // ACTIVE, INACTIVE, SUSPENDED
    private Boolean riskCheckEnabled;

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
        return traderId != null && !traderId.isBlank() &&
               name != null && !name.isBlank() &&
               maxOrderSize != null && maxOrderSize > 0 &&
               maxNotionalPerDay != null && maxNotionalPerDay > 0;
    }
}

