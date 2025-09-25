package com.java_template.application.entity.study.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Study Entity for Clinical Trial Management
 * 
 * Represents a clinical research study with all relevant metadata,
 * protocol information, and lifecycle management.
 */
@Data
public class Study implements CyodaEntity {
    public static final String ENTITY_NAME = Study.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String studyId;
    
    // Core study information
    private String title;
    private String shortTitle;
    private String description;
    private String phase; // Phase I, II, III, IV
    private String studyType; // Interventional, Observational, etc.
    private String primaryObjective;
    private String secondaryObjective;
    
    // Protocol information
    private String protocolNumber;
    private String protocolVersion;
    private LocalDate protocolDate;
    
    // Sponsor and regulatory information
    private SponsorInfo sponsor;
    private List<RegulatoryInfo> regulatoryApprovals;
    
    // Study timeline
    private LocalDate plannedStartDate;
    private LocalDate actualStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualEndDate;
    private LocalDate firstPatientIn;
    private LocalDate lastPatientOut;
    
    // Enrollment information
    private Integer plannedEnrollment;
    private Integer actualEnrollment;
    private Integer currentEnrollment;
    
    // Study design
    private StudyDesign design;
    
    // Contact information
    private List<StudyContact> contacts;
    
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
        return studyId != null && !studyId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               phase != null && !phase.trim().isEmpty();
    }

    /**
     * Sponsor information for the study
     */
    @Data
    public static class SponsorInfo {
        private String name;
        private String type; // Commercial, Academic, Government
        private String contactPerson;
        private String email;
        private String phone;
        private Address address;
    }

    /**
     * Regulatory approval information
     */
    @Data
    public static class RegulatoryInfo {
        private String authority; // FDA, EMA, etc.
        private String approvalNumber;
        private LocalDate approvalDate;
        private String status; // Approved, Pending, Denied
        private String country;
    }

    /**
     * Study design information
     */
    @Data
    public static class StudyDesign {
        private String allocation; // Randomized, Non-Randomized
        private String intervention; // Single Group, Parallel, Crossover
        private String masking; // Open Label, Single Blind, Double Blind
        private String primaryPurpose; // Treatment, Prevention, Diagnostic
        private List<String> inclusionCriteria;
        private List<String> exclusionCriteria;
        private List<StudyArm> arms;
    }

    /**
     * Study arm information
     */
    @Data
    public static class StudyArm {
        private String armId;
        private String label;
        private String type; // Experimental, Active Comparator, Placebo
        private String description;
        private String intervention;
    }

    /**
     * Study contact information
     */
    @Data
    public static class StudyContact {
        private String name;
        private String role; // Principal Investigator, Study Coordinator, etc.
        private String email;
        private String phone;
        private String affiliation;
        private Address address;
    }

    /**
     * Address information
     */
    @Data
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }
}
