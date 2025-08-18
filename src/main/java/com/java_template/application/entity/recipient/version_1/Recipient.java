package com.java_template.application.entity.recipient.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Recipient implements CyodaEntity {
    public static final String ENTITY_NAME = "Recipient";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String technicalId; // platform technical id
    private String email; // email address
    private String name; // display name
    private Map<String, Object> preferences; // optOut boolean, allowedCategories array
    private String status; // new verified opted_out invalid
    private String createdAt; // timestamp
    private String updatedAt; // timestamp

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
        // Basic validation: email must be present and non-blank and contain '@'
        return this.email != null && !this.email.isBlank() && this.email.contains("@");
    }
}
