package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private Double amount;
    private String approvedAt; // ISO-8601 timestamp or null if not approved
    private String cartId; // foreign key (serialized UUID)
    private String createdAt; // ISO-8601 timestamp
    private String orderId; // foreign key (serialized UUID), may be null until order created
    private String status; // use String for enum-like status
    private String userId; // foreign key (serialized UUID)

    public Payment() {} 

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
        if (id == null || id.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        // Validate amount
        if (amount == null) return false;
        if (amount < 0) return false;
        return true;
    }
}