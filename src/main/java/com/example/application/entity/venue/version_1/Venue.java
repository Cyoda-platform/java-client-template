package com.example.application.entity.venue.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Venue Entity - Represents execution venues for order routing
 * 
 * Stores venue information and supported symbols for routing decisions.
 */
@Data
public class Venue implements CyodaEntity {
    public static final String ENTITY_NAME = "Venue";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String venueId;

    // Venue information
    private String name;
    private String type; // EXCHANGE, DARK_POOL, BROKER, etc.
    private String routingRule; // DIRECT, SMART_ROUTER, etc.

    // Supported symbols and liquidity
    private List<String> supportedSymbols;
    private Double averageLiquidity; // Average liquidity in notional

    // Status
    private String status; // ACTIVE, INACTIVE, MAINTENANCE
    private Boolean available;

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
        return venueId != null && !venueId.isBlank() &&
               name != null && !name.isBlank() &&
               supportedSymbols != null && !supportedSymbols.isEmpty();
    }
}

