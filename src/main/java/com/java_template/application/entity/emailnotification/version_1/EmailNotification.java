package com.java_template.application.entity.emailnotification.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class EmailNotification implements CyodaEntity {
    public static final String ENTITY_NAME = EmailNotification.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Unique identifier
    private Long id;
    
    // Reference to Report entity
    private Long reportId;
    
    // Email address of recipient
    private String recipientEmail;
    
    // Email subject line
    private String subject;
    
    // Email body content
    private String bodyContent;
    
    // Path to report attachment
    private String attachmentPath;
    
    // When email should be sent
    private LocalDateTime scheduledSendTime;
    
    // When email was actually sent
    private LocalDateTime actualSendTime;
    
    // Email delivery status (PENDING, SENT, FAILED, DELIVERED)
    private String deliveryStatus;
    
    // Error details if delivery failed
    private String errorMessage;
    
    // Number of retry attempts
    private Integer retryCount;
    
    // Maximum retry attempts allowed
    private Integer maxRetries;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return recipientEmail != null && !recipientEmail.trim().isEmpty() &&
               recipientEmail.contains("@") &&
               subject != null && !subject.trim().isEmpty() &&
               bodyContent != null && !bodyContent.trim().isEmpty() &&
               reportId != null && reportId > 0;
    }
}
