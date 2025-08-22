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
    // Technical id (serialized UUID)
    private String id;
    private String name;
    private String contactDetails;
    private String contactMethod;
    private String preference;
    private String status;
    // JSON string representing filter criteria
    private String filters;
    // ISO-8601 timestamp string
    private String createdTimestamp;

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
        // Validate required string fields using isBlank (and null check to avoid NPE)
        if (name == null || name.isBlank()) return false;
        if (contactDetails == null || contactDetails.isBlank()) return false;
        if (contactMethod == null || contactMethod.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdTimestamp == null || createdTimestamp.isBlank()) return false;
        // filters and preference are optional
        return true;
    }
}