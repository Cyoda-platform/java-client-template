package com.java_template.application.entity.mail.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String technicalId; // UUID assigned on create
    private String subject; // message subject
    private String body; // message body / HTML
    private Boolean isHappy; // true/false/null if unknown
    private Double classificationConfidence; // 0..1 confidence score
    private String templateId; // optional template reference used when sending
    private Object mailList; // reference to MailingList id (String) or inline Recipient emails (List<String>)
    private String status; // draft scheduled queued sending sent failed review
    private String createdBy; // user id
    private String createdAt; // timestamp
    private String updatedAt; // timestamp
    private Map<String, Object> meta; // free-form metadata

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
        // Basic validation: require subject, body and createdBy to be present and non-blank
        return this.subject != null && !this.subject.isBlank()
            && this.body != null && !this.body.isBlank()
            && this.createdBy != null && !this.createdBy.isBlank();
    }
}
