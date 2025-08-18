package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail";
    public static final Integer ENTITY_VERSION = 1;

    // Mail fields
    private String id; // business id
    private String subject; // message subject
    private String body; // message body
    private Boolean isHappy; // true/false/null if unknown
    private String mailList; // reference to MailingList id or serialized recipient emails
    private String status; // draft scheduled queued sending sent failed review
    private String createdBy; // user id
    private String createdAt; // timestamp

    public Mail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // subject and body must not be blank, createdBy must be present
        if (subject == null || subject.isBlank()) return false;
        if (body == null || body.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;
        return true;
    }
}
