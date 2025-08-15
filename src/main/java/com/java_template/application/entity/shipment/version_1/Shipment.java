package com.java_template.application.entity.shipment.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = "Shipment";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String orderId; // serialized UUID
    private String carrier;
    private String trackingNumber;
    private String status; // e.g., CREATED, SHIPPED, DELIVERED
    private String shippedAt;
    private String estimatedDelivery;
    private String destinationAddress;

    public Shipment() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (carrier == null || carrier.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (destinationAddress == null || destinationAddress.isBlank()) return false;
        return true;
    }
}
