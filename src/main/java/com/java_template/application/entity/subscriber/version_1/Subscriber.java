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
    // Technical id (e.g., generated UUID as String)
    private String id;
    // Natural subscriber identifier from requirements example
    private String subscriberId;
    // Whether the subscriber is active
    private Boolean active;
    // Contact information (e.g., webhook URL)
    private String contactAddress;
    // Contact type (use String rather than enum per rules)
    private String contactType;
    // Optional filters expressed as a string (e.g., "category=Chemistry")
    private String filters;
    // Timestamp of last notification as ISO string
    private String lastNotifiedAt;

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
        if (contactAddress == null || contactAddress.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        // Active should be provided
        if (active == null) return false;
        // lastNotifiedAt and filters are optional
        return true;
    }
}