package com.java_template.application.entity.mailinglist.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class MailingList implements CyodaEntity {
    public static final String ENTITY_NAME = "MailingList";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String name; // display name
    private List<String> recipients; // list of Recipient ids or emails (serialized)
    private Boolean isActive; // list active flag
    private String createdAt; // timestamp

    public MailingList() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: require name to be present and non-blank
        return this.name != null && !this.name.isBlank();
    }
}
