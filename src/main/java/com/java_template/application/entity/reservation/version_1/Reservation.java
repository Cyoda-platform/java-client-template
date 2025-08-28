package com.java_template.application.entity.reservation.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Reservation implements CyodaEntity {
    public static final String ENTITY_NAME = "Reservation"; 
    public static final Integer ENTITY_VERSION = 1;
    // Entity fields based on prototype
    // Technical id
    private String id;
    // References (serialized UUIDs as Strings)
    private String cartId;
    private String productId;
    private String warehouseId;
    // Quantity reserved
    private Integer qty;
    // Status (use String for enum-like values)
    private String status;
    // Timestamps in ISO-8601 string format
    private String createdAt;
    private String expiresAt;

    public Reservation() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields (check null or blank)
        if (id == null || id.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (productId == null || productId.isBlank()) return false;
        if (warehouseId == null || warehouseId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (expiresAt == null || expiresAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Validate quantity
        if (qty == null || qty <= 0) return false;
        return true;
    }
}