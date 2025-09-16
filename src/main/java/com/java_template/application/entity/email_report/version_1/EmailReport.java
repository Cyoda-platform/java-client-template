package com.java_template.application.entity.email_report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * EmailReport Entity - Represents email reports sent containing comment analysis results
 * 
 * This entity manages the email delivery process for comment analysis reports,
 * including retry logic and delivery status tracking.
 */
@Data
public class EmailReport implements CyodaEntity {
    public static final String ENTITY_NAME = EmailReport.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - generated UUID as string
    private String reportId;
    
    // Required core business fields
    private String analysisId;
    private Integer postId;
    private String recipientEmail;
    private String subject;
    private String reportContent;
    
    // Optional delivery tracking fields
    private LocalDateTime sentAt;
    private String deliveryStatus; // PENDING, SENT, FAILED
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime lastRetryAt;

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
        if (reportId == null || reportId.trim().isEmpty()) {
            return false;
        }
        if (analysisId == null || analysisId.trim().isEmpty()) {
            return false;
        }
        if (postId == null || postId <= 0) {
            return false;
        }
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            return false;
        }
        if (subject == null || subject.trim().isEmpty()) {
            return false;
        }
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return false;
        }
        
        // Basic email format validation
        if (!recipientEmail.contains("@") || !recipientEmail.contains(".")) {
            return false;
        }
        
        return true;
    }
}
