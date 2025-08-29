package com.java_template.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String createdAt; // ISO-8601 timestamp string
    private String customerId; // technical/business id (serialized UUID or string)
    private String email;
    private String name;
    private String phone;
    private String status;

    public Customer() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank checks
        if (customerId == null || customerId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Basic email sanity check
        if (!email.contains("@")) return false;
        return true;
    }
}