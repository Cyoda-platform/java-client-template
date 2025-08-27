package com.java_template.application.entity.pickledger.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class PickLedger implements CyodaEntity {
    public static final String ENTITY_NAME = "PickLedger"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String pickId;      // technical id (serialized UUID)
    private String orderId;     // foreign key (serialized UUID)
    private String shipmentId;  // foreign key (serialized UUID)
    private String sku;
    private String actor;
    private String at;          // ISO-8601 timestamp string
    private Integer delta;
    private String note;

    public PickLedger() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be non-null and not blank
        if (pickId == null || pickId.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (actor == null || actor.isBlank()) return false;
        if (at == null || at.isBlank()) return false;
        // delta must be present (can be positive or negative depending on operation)
        if (delta == null) return false;
        // Optional references, if provided, must not be blank
        if (orderId != null && orderId.isBlank()) return false;
        if (shipmentId != null && shipmentId.isBlank()) return false;
        if (note != null && note.isBlank()) return false;
        return true;
    }
}