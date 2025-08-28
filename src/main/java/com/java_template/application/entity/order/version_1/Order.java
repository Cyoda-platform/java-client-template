package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical identifier (serialized UUID or similar)
    private String orderId;

    // Reference to Pet entity (serialized UUID)
    private String petId;

    // Buyer information
    private String buyerName;
    private String buyerContact;

    // Order details
    private String type;    // use String for enum-like values (e.g., "adoption")
    private String status;  // use String for enum-like values (e.g., "PLACED")
    private String placedAt; // ISO-8601 timestamp as String

    // Optional notes
    private String notes;

    public Order() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public boolean isValid() {
        // Required fields: orderId, petId, buyerName, buyerContact, type, status, placedAt
        return notBlank(orderId)
                && notBlank(petId)
                && notBlank(buyerName)
                && notBlank(buyerContact)
                && notBlank(type)
                && notBlank(status)
                && notBlank(placedAt);
    }
}