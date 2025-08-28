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
    private String subscriberId; // technical id (serialized UUID or similar)
    private String email;
    private String name;
    private String filters; // serialized filter string, e.g., "area=NW"
    private String frequency; // e.g., "weekly"
    private String status; // e.g., "ACTIVE"

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
        // Validate required string fields using isBlank()
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (frequency == null || frequency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // name and filters can be optional, but if present should not be blank
        if (name != null && name.isBlank()) return false;
        if (filters != null && filters.isBlank()) return false;
        return true;
    }
}