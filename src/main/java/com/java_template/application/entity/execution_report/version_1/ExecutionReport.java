package com.java_template.application.entity.execution_report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ExecutionReport Entity - Represents execution reports for filled orders
 * 
 * Emitted when orders are filled or partially filled at execution venues.
 */
@Data
public class ExecutionReport implements CyodaEntity {
    public static final String ENTITY_NAME = "ExecutionReport";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String executionId;

    // Reference to order
    private String orderId;

    // Execution details
    private Long filledQuantity;
    private Long remainingQuantity;
    private Double price;
    private String status; // FILLED, PARTIALLY_FILLED, REJECTED, etc.
    private LocalDateTime timestamp;

    // Venue information
    private String venueId;
    private String executionVenue;

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
        return executionId != null && !executionId.isBlank() &&
               orderId != null && !orderId.isBlank() &&
               filledQuantity != null && filledQuantity >= 0 &&
               price != null && price > 0;
    }
}

