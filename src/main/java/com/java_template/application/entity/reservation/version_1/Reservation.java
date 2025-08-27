package com.java_template.application.entity.reservation.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Reservation implements CyodaEntity {
    public static final String ENTITY_NAME = "Reservation"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String reservationId; // technical id (serialized UUID)
    private String cartId; // foreign key (serialized UUID)
    private String reservationBatchId;
    private String sku;
    private Integer qty;
    private String status;
    private String expiresAt; // ISO-8601 timestamp string

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
        // Validate required string fields using isBlank()
        if (reservationId == null || reservationId.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (reservationBatchId == null || reservationBatchId.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (expiresAt == null || expiresAt.isBlank()) return false;
        // Validate quantity
        if (qty == null || qty <= 0) return false;
        return true;
    }
}