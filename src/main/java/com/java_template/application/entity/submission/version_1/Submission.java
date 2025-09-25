package com.java_template.application.entity.submission.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission Entity for Clinical Trial Management
 * 
 * Represents a clinical trial submission with all required fields for the MVP.
 * Based on functional requirements from PRD and API Data Contracts.
 */
@Data
public class Submission implements CyodaEntity {
    public static final String ENTITY_NAME = Submission.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String submissionId;
    
    // Core submission fields
    private String title;
    private String studyType; // "clinical_trial", "observational", "lab_research", "other"
    private String protocolId;
    private String phase; // "I", "II", "III", "IV", "N/A"
    private String therapeuticArea;
    private String sponsorName;
    private String principalInvestigator;
    private List<String> sites;
    private LocalDate startDate;
    private LocalDate endDate;
    private String fundingSource;
    private String riskCategory; // "low", "moderate", "high"
    private List<String> keywords;
    
    // Declarations and attestations
    private Declarations declarations;
    
    // Document tracking
    private List<String> attachmentsRequired;
    private List<String> attachmentsProvided;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

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
        return submissionId != null && !submissionId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               studyType != null && !studyType.trim().isEmpty() &&
               protocolId != null && !protocolId.trim().isEmpty() &&
               sponsorName != null && !sponsorName.trim().isEmpty() &&
               principalInvestigator != null && !principalInvestigator.trim().isEmpty();
    }

    /**
     * Nested class for declarations and attestations
     */
    @Data
    public static class Declarations {
        private Boolean conflictsOfInterest;
        private List<String> attestations;
    }
}
