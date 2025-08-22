package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id, e.g., "ORD-777"
    private String userId; // foreign key reference (serialized UUID or technical id), e.g., "USR-99"
    private String petId; // foreign key reference (serialized UUID or technical id), e.g., "PET-123"
    private String type; // e.g., "adopt"
    private String status; // e.g., "initiated"
    private Integer total; // e.g., 50
    private String notes;
    private String createdAt; // ISO-8601 timestamp as String, e.g., "2025-08-22T10:00:00Z"
    private String expiresAt; // ISO-8601 timestamp as String

    public Order() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: required string fields must be non-null and non-blank
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (expiresAt == null || expiresAt.isBlank()) return false;
        // total must be present and non-negative
        if (total == null || total < 0) return false;
        return true;
    }
}