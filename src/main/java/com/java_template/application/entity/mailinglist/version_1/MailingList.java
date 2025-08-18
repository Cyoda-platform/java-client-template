package com.java_template.application.entity.mailinglist.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class MailingList implements CyodaEntity {
    public static final String ENTITY_NAME = "MailingList";
    public static final Integer ENTITY_VERSION = 1;

    // MailingList fields
    private String id; // business id
    private String name; // display name
    private List<String> recipients; // list of Recipient ids or emails
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
        // name must not be blank
        if (name == null || name.isBlank()) return false;
        // recipients can be empty but must not be null
        if (recipients == null) return false;
        return true;
    }
}
