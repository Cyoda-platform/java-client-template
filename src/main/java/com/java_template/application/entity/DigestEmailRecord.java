package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestEmailRecord implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String jobTechnicalId; // reference to DigestRequestJob technicalId
    private String emailContent; // compiled digest content, HTML or plain text
    private String emailSentAt; // timestamp when email was dispatched
    private String emailStatus; // status of email sending: SENT, FAILED

    public DigestEmailRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) return false;
        if (emailContent == null || emailContent.isBlank()) return false;
        if (emailSentAt == null || emailSentAt.isBlank()) return false;
        if (emailStatus == null || emailStatus.isBlank()) return false;
        return true;
    }
}
