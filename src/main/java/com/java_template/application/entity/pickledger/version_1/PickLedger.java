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
    private String id;
    private String auditStatus; // enum as String (e.g., "AUDIT_PASSED")
    private String auditorId; // foreign key as serialized UUID string
    private String orderId; // foreign key as serialized UUID string
    private String productId; // foreign key as serialized UUID string
    private Integer qtyPicked;
    private Integer qtyRequested;
    private String shipmentId; // foreign key as serialized UUID string
    private String timestamp; // ISO-8601 string

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
        // id, orderId, productId, shipmentId and timestamp must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (productId == null || productId.isBlank()) return false;
        if (shipmentId == null || shipmentId.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;

        // qtyRequested and qtyPicked must be present and non-negative
        if (qtyRequested == null || qtyRequested < 0) return false;
        if (qtyPicked == null || qtyPicked < 0) return false;

        // qtyPicked should not exceed qtyRequested
        if (qtyPicked > qtyRequested) return false;

        // If auditStatus or auditorId provided, they must not be blank
        if (auditStatus != null && auditStatus.isBlank()) return false;
        if (auditorId != null && auditorId.isBlank()) return false;

        return true;
    }
}