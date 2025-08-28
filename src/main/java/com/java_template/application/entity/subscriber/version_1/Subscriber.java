package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or string id)
    private Boolean active;
    private String contactDetail;
    private String contactType;
    private String createdAt; // ISO-8601 timestamp as string
    private Map<String, String> filters; // arbitrary filter key-values (e.g., category, year)

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
        // Validate required string fields using isBlank checks
        if (id == null || id.isBlank()) return false;
        if (contactDetail == null || contactDetail.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // Boolean must be present
        if (active == null) return false;
        return true;
    }
}