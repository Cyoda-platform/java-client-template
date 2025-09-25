package com.java_template.application.entity.study.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Study Entity for Operational Trial Management
 * 
 * Represents an approved clinical study with operational management capabilities.
 * Created from approved submissions for operational conduct.
 */
@Data
public class Study implements CyodaEntity {
    public static final String ENTITY_NAME = Study.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String studyId;
    
    // Core study fields
    private String title;
    private String protocolId;
    private String phase; // "I", "II", "III", "IV", "N/A"
    private String therapeuticArea;
    private String sponsorName;
    private String principalInvestigator;
    
    // Study design
    private List<StudyArm> arms;
    private List<VisitSchedule> visitSchedule;
    private List<String> sites;
    
    // Study dates
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    
    // Study status and metrics
    private String status; // "active", "completed", "suspended", "terminated"
    private Integer targetEnrollment;
    private Integer currentEnrollment;
    
    // Relationships
    private String sourceSubmissionId; // Link to original submission
    
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
        return studyId != null && !studyId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               protocolId != null && !protocolId.trim().isEmpty() &&
               principalInvestigator != null && !principalInvestigator.trim().isEmpty();
    }

    /**
     * Nested class for study arms
     */
    @Data
    public static class StudyArm {
        private String armId;
        private String name;
        private String description;
        private String treatment;
        private Integer targetSize;
    }

    /**
     * Nested class for visit schedule
     */
    @Data
    public static class VisitSchedule {
        private String visitCode;
        private String name;
        private Integer windowMinusDays;
        private Integer windowPlusDays;
        private List<String> procedures;
        private Boolean isMandatory;
    }
}
