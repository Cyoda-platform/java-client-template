package com.java_template.application.entity.recipient.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Recipient implements CyodaEntity {
    public static final String ENTITY_NAME = "Recipient";
    public static final Integer ENTITY_VERSION = 1;

    // Recipient fields
    private String id; // business id
    private String email; // email address
    private String name; // display name
    private Map<String, Object> preferences; // preferences object (optOut boolean, allowedCategories array)
    private String status; // new verified opted_out invalid
    private String createdAt; // timestamp

    public Recipient() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // email must be a non-blank string and contain '@'
        if (email == null || email.isBlank()) return false;
        if (!email.contains("@")) return false;
        return true;
    }
}
