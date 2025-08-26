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
    private String id;
    private Boolean active;
    private String contact;
    private String createdAt;
    private String filters; // JSON serialized filters
    private String lastNotifiedAt;
    private String type;

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
        // id, contact, type and createdAt are mandatory. active must be present.
        if (id == null || id.isBlank()) return false;
        if (active == null) return false;
        if (contact == null || contact.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // filters and lastNotifiedAt are optional
        return true;
    }
}