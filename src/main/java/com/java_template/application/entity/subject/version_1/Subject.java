package com.java_template.application.entity.subject.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Subject Entity for Study Participants
 * 
 * Represents a study participant with enrollment and consent tracking.
 */
@Data
public class Subject implements CyodaEntity {
    public static final String ENTITY_NAME = Subject.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String subjectId;
    
    // Study relationship
    private String studyId;
    
    // Subject identifiers
    private String screeningId;
    private String enrollmentId;
    
    // Enrollment information
    private LocalDate enrollmentDate;
    private String status; // "screening", "enrolled", "completed", "withdrawn"
    
    // Demographics
    private Demographics demographics;
    
    // Consent information
    private String consentStatus; // "not_consented", "consented", "withdrawn"
    private LocalDate consentDate;
    private String consentVersion;
    
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
        return subjectId != null && !subjectId.trim().isEmpty() &&
               studyId != null && !studyId.trim().isEmpty() &&
               status != null && !status.trim().isEmpty();
    }

    /**
     * Nested class for demographics
     */
    @Data
    public static class Demographics {
        private Integer age;
        private String sexAtBirth; // "female", "male", "intersex", "unknown"
        private String notes;
    }
}
