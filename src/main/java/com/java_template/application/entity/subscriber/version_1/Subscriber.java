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
    private String id; // technical id (serialized UUID or similar)
    private String name;
    private Boolean active;
    private String contactDetails;
    private String contactType;
    private String filterPreferences; // JSON string representing filter preferences
    private String lastNotifiedAt; // ISO-8601 timestamp as String

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactDetails == null || contactDetails.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        // active must be present
        if (active == null) return false;
        return true;
    }
}