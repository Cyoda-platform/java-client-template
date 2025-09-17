package com.java_template.application.entity.emailnotification.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailNotification Entity
 * Manages sending analysis reports via email to a list of subscribers.
 */
@Data
public class EmailNotification implements CyodaEntity {
    public static final String ENTITY_NAME = EmailNotification.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String notificationId;
    
    // Required core business fields
    private String analysisId;
    private List<String> subscriberEmails;
    
    // Optional fields for additional business data
    private String emailSubject;
    private String emailBody;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return notificationId != null && !notificationId.trim().isEmpty() 
            && analysisId != null && !analysisId.trim().isEmpty()
            && subscriberEmails != null && !subscriberEmails.isEmpty();
    }
}
