package com.java_template.application.entity.protocol.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Protocol Entity for Clinical Trial Management
 * 
 * Represents a clinical study protocol with detailed procedures,
 * inclusion/exclusion criteria, and version control.
 */
@Data
public class Protocol implements CyodaEntity {
    public static final String ENTITY_NAME = Protocol.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String protocolId;
    
    // Basic protocol information
    private String protocolNumber;
    private String protocolTitle;
    private String shortTitle;
    private String protocolVersion;
    private LocalDate protocolDate;
    private String studyId;
    
    // Protocol details
    private String primaryObjective;
    private String secondaryObjective;
    private String background;
    private String rationale;
    private String studyDesign;
    private String phase; // Phase I, II, III, IV
    private String studyType; // Interventional, Observational
    
    // Participant criteria
    private EligibilityCriteria eligibilityCriteria;
    
    // Study procedures
    private List<StudyProcedure> procedures;
    private List<StudyVisit> visitSchedule;
    private List<Assessment> assessments;
    
    // Intervention details
    private List<Intervention> interventions;
    private List<Medication> medications;
    
    // Safety information
    private SafetyInformation safety;
    
    // Statistical information
    private StatisticalPlan statisticalPlan;
    
    // Regulatory information
    private List<RegulatoryRequirement> regulatoryRequirements;
    
    // Version control
    private List<ProtocolAmendment> amendments;
    
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
        return protocolId != null && !protocolId.trim().isEmpty() &&
               protocolNumber != null && !protocolNumber.trim().isEmpty() &&
               protocolTitle != null && !protocolTitle.trim().isEmpty() &&
               protocolVersion != null && !protocolVersion.trim().isEmpty();
    }

    /**
     * Eligibility criteria
     */
    @Data
    public static class EligibilityCriteria {
        private List<String> inclusionCriteria;
        private List<String> exclusionCriteria;
        private AgeRange ageRange;
        private List<String> allowedGenders;
        private List<String> requiredConditions;
        private List<String> prohibitedMedications;
        private List<String> prohibitedConditions;
    }

    /**
     * Age range specification
     */
    @Data
    public static class AgeRange {
        private Integer minimumAge;
        private Integer maximumAge;
        private String ageUnit; // Years, Months, Days
    }

    /**
     * Study procedure
     */
    @Data
    public static class StudyProcedure {
        private String procedureId;
        private String procedureName;
        private String description;
        private String category; // Screening, Treatment, Assessment, Safety
        private String frequency;
        private String duration;
        private List<String> requiredEquipment;
        private List<String> requiredPersonnel;
        private String instructions;
        private Boolean mandatory;
    }

    /**
     * Study visit
     */
    @Data
    public static class StudyVisit {
        private String visitId;
        private String visitName;
        private String visitType; // Screening, Baseline, Treatment, Follow-up
        private Integer visitNumber;
        private String timepoint; // Day 1, Week 2, Month 3, etc.
        private String window; // ±3 days, ±1 week, etc.
        private List<String> procedureIds;
        private List<String> assessmentIds;
        private String duration; // Expected visit duration
        private Boolean mandatory;
        private String instructions;
    }

    /**
     * Assessment
     */
    @Data
    public static class Assessment {
        private String assessmentId;
        private String assessmentName;
        private String assessmentType; // Efficacy, Safety, Biomarker, QoL
        private String description;
        private String method;
        private String frequency;
        private String dataCollection;
        private Boolean primaryEndpoint;
        private Boolean secondaryEndpoint;
        private String instructions;
    }

    /**
     * Intervention
     */
    @Data
    public static class Intervention {
        private String interventionId;
        private String interventionName;
        private String interventionType; // Drug, Device, Procedure, Behavioral
        private String description;
        private String dosage;
        private String frequency;
        private String route; // Oral, IV, IM, etc.
        private String duration;
        private String instructions;
        private List<String> contraindications;
    }

    /**
     * Medication
     */
    @Data
    public static class Medication {
        private String medicationId;
        private String medicationName;
        private String activeIngredient;
        private String dosageForm; // Tablet, Capsule, Injection
        private String strength;
        private String manufacturer;
        private String lotNumber;
        private LocalDate expirationDate;
        private String storageConditions;
        private String dispensingInstructions;
    }

    /**
     * Safety information
     */
    @Data
    public static class SafetyInformation {
        private List<String> knownRisks;
        private List<String> potentialRisks;
        private List<String> safetyMonitoring;
        private String stoppingRules;
        private String adverseEventReporting;
        private String emergencyProcedures;
        private String dataMonitoringCommittee;
        private List<String> safetyEndpoints;
    }

    /**
     * Statistical plan
     */
    @Data
    public static class StatisticalPlan {
        private String primaryEndpoint;
        private List<String> secondaryEndpoints;
        private Integer sampleSize;
        private String sampleSizeJustification;
        private String randomizationMethod;
        private String stratificationFactors;
        private String analysisPopulation;
        private String statisticalMethods;
        private Double significanceLevel;
        private Double power;
        private String interimAnalysis;
    }

    /**
     * Regulatory requirement
     */
    @Data
    public static class RegulatoryRequirement {
        private String authority; // FDA, EMA, etc.
        private String requirementType; // IND, CTA, etc.
        private String requirementNumber;
        private LocalDate submissionDate;
        private LocalDate approvalDate;
        private String status; // Submitted, Approved, Pending
        private String country;
    }

    /**
     * Protocol amendment
     */
    @Data
    public static class ProtocolAmendment {
        private String amendmentNumber;
        private String amendmentVersion;
        private LocalDate amendmentDate;
        private String amendmentType; // Administrative, Safety, Efficacy
        private String description;
        private String rationale;
        private List<String> changesDescription;
        private String approvalStatus;
        private LocalDate approvalDate;
        private String approvedBy;
    }
}
