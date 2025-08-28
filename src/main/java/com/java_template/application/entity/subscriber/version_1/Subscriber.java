package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or string)
    private Boolean active;
    private String createdAt; // ISO-8601 timestamp
    private String email;
    private String filters; // serialized filter expression
    private String name;
    private String webhookUrl; // optional

    public Subscriber() {} 

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
        if (active == null) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (filters == null || filters.isBlank()) return false;
        // webhookUrl is optional
        return true;
    }
}