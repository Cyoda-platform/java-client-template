package com.java_template.application.entity.submission.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Submission Entity for Research & Clinical Trial Management platform
 * Represents research or clinical trial submissions that go through review workflow
 */
@Data
public class Submission implements CyodaEntity {
    public static final String ENTITY_NAME = Submission.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core business fields
    private String title;
    private String description;
    private String submitterEmail;
    private String submissionType; // RESEARCH_PROPOSAL, CLINICAL_TRIAL, ETHICS_REVIEW
    private String priority; // LOW, MEDIUM, HIGH, URGENT
    private LocalDateTime submissionDate;
    private LocalDateTime targetDecisionDate;
    
    // Optional fields
    private String reviewerEmail; // Assigned reviewer's email (nullable)
    private String decisionReason; // Reason for final decision (nullable)

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
        return title != null && !title.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               submitterEmail != null && !submitterEmail.trim().isEmpty() &&
               submissionType != null && !submissionType.trim().isEmpty() &&
               priority != null && !priority.trim().isEmpty() &&
               submissionDate != null &&
               targetDecisionDate != null &&
               isValidSubmissionType(submissionType) &&
               isValidPriority(priority) &&
               isValidEmail(submitterEmail) &&
               (reviewerEmail == null || isValidEmail(reviewerEmail)) &&
               targetDecisionDate.isAfter(submissionDate);
    }

    /**
     * Validates if the submission type is one of the allowed values
     */
    private boolean isValidSubmissionType(String type) {
        return "RESEARCH_PROPOSAL".equals(type) || 
               "CLINICAL_TRIAL".equals(type) || 
               "ETHICS_REVIEW".equals(type);
    }

    /**
     * Validates if the priority is one of the allowed values
     */
    private boolean isValidPriority(String priority) {
        return "LOW".equals(priority) || 
               "MEDIUM".equals(priority) || 
               "HIGH".equals(priority) ||
               "URGENT".equals(priority);
    }

    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}
