package com.java_template.application.entity.emailreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class EmailReport implements CyodaEntity {
    public static final String ENTITY_NAME = EmailReport.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String requestId;
    private String recipientEmail;
    private String subject;
    private String htmlContent;
    private String textContent;
    private LocalDateTime sentAt;
    private String emailStatus;
    private String reportId;
    private LocalDateTime sendingStartedAt;
    private LocalDateTime failedAt;
    private String failureReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return requestId != null && !requestId.trim().isEmpty() &&
               recipientEmail != null && !recipientEmail.trim().isEmpty() &&
               recipientEmail.contains("@") &&
               reportId != null && !reportId.trim().isEmpty();
    }
}
