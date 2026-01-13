package com.example.application.entity.executionvenue.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ExecutionVenue entity representing a trading venue or exchange
 */
@Data
public class ExecutionVenue implements CyodaEntity {
    public static final String ENTITY_NAME = "ExecutionVenue";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String venueId;

    // Core fields
    private String venueName;
    private String venueType; // EXCHANGE, DARK_POOL, ATS, BROKER
    private String country;
    private String status; // ACTIVE, INACTIVE, SUSPENDED

    // Connectivity
    private String apiEndpoint;
    private String protocol; // FIX, REST, WEBSOCKET
    private Integer priority; // Lower number = higher priority

    // Operational details
    private String operatingHours;
    private Boolean supportsPreMarket;
    private Boolean supportsAfterHours;

    // Timestamps
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
        return venueId != null && !venueId.isBlank() &&
               venueName != null && !venueName.isBlank();
    }
}

