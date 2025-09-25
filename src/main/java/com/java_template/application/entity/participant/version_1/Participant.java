package com.java_template.application.entity.participant.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Participant Entity for Clinical Trial Management
 * 
 * Represents a study participant with demographics, enrollment information,
 * consent details, and medical history.
 */
@Data
public class Participant implements CyodaEntity {
    public static final String ENTITY_NAME = Participant.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String participantId;
    
    // Study assignment
    private String studyId;
    private String siteId;
    private String investigatorId;
    
    // Demographics
    private Demographics demographics;
    
    // Enrollment information
    private EnrollmentInfo enrollment;
    
    // Consent information
    private List<ConsentInfo> consents;
    
    // Medical information
    private MedicalHistory medicalHistory;
    private List<Medication> medications;
    private List<Allergy> allergies;
    
    // Study participation
    private String randomizationCode;
    private String treatmentArm;
    private List<StudyVisit> visits;
    private List<AdverseEvent> adverseEvents;
    
    // Contact information
    private ContactInfo contact;
    private EmergencyContact emergencyContact;
    
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
        return participantId != null && !participantId.trim().isEmpty() &&
               studyId != null && !studyId.trim().isEmpty() &&
               demographics != null && demographics.isValid();
    }

    /**
     * Participant demographics
     */
    @Data
    public static class Demographics {
        private LocalDate dateOfBirth;
        private String gender; // Male, Female, Other
        private String race;
        private String ethnicity;
        private Double height; // in cm
        private Double weight; // in kg
        private String educationLevel;
        private String occupation;
        private String maritalStatus;
        
        public boolean isValid() {
            return dateOfBirth != null && gender != null && !gender.trim().isEmpty();
        }
    }

    /**
     * Enrollment information
     */
    @Data
    public static class EnrollmentInfo {
        private LocalDate enrollmentDate;
        private String enrollmentStatus; // Enrolled, Withdrawn, Completed, Screen Failed
        private LocalDate withdrawalDate;
        private String withdrawalReason;
        private String screeningNumber;
        private LocalDate screeningDate;
        private Boolean eligibilityConfirmed;
    }

    /**
     * Consent information
     */
    @Data
    public static class ConsentInfo {
        private String consentType; // Main Study, Genetic Testing, Data Sharing
        private String consentVersion;
        private LocalDate consentDate;
        private Boolean consentGiven;
        private String consentForm;
        private LocalDate withdrawalDate;
        private String witnessName;
        private String obtainedBy;
    }

    /**
     * Medical history
     */
    @Data
    public static class MedicalHistory {
        private List<MedicalCondition> conditions;
        private List<Surgery> surgeries;
        private List<String> familyHistory;
        private String smokingStatus;
        private String alcoholConsumption;
        private String exerciseHabits;
    }

    /**
     * Medical condition
     */
    @Data
    public static class MedicalCondition {
        private String condition;
        private String severity; // Mild, Moderate, Severe
        private LocalDate onsetDate;
        private LocalDate resolvedDate;
        private Boolean ongoing;
        private String treatment;
    }

    /**
     * Surgery information
     */
    @Data
    public static class Surgery {
        private String procedure;
        private LocalDate surgeryDate;
        private String surgeon;
        private String hospital;
        private String complications;
    }

    /**
     * Medication information
     */
    @Data
    public static class Medication {
        private String medicationName;
        private String dosage;
        private String frequency;
        private String route; // Oral, IV, IM, etc.
        private LocalDate startDate;
        private LocalDate endDate;
        private String indication;
        private Boolean concomitant; // Taken during study
    }

    /**
     * Allergy information
     */
    @Data
    public static class Allergy {
        private String allergen;
        private String reaction;
        private String severity; // Mild, Moderate, Severe, Life-threatening
        private LocalDate onsetDate;
        private String treatment;
    }

    /**
     * Study visit information
     */
    @Data
    public static class StudyVisit {
        private String visitId;
        private String visitName;
        private LocalDate scheduledDate;
        private LocalDate actualDate;
        private String visitStatus; // Scheduled, Completed, Missed, Cancelled
        private String visitType; // Screening, Baseline, Follow-up, Unscheduled
        private List<String> proceduresCompleted;
        private String notes;
    }

    /**
     * Adverse event information
     */
    @Data
    public static class AdverseEvent {
        private String eventId;
        private String description;
        private String severity; // Mild, Moderate, Severe
        private String seriousness; // Serious, Non-serious
        private LocalDate onsetDate;
        private LocalDate resolvedDate;
        private String outcome; // Recovered, Ongoing, Fatal, etc.
        private String causality; // Related, Unrelated, Possibly Related
        private String action; // None, Dose Reduced, Drug Withdrawn
    }

    /**
     * Contact information
     */
    @Data
    public static class ContactInfo {
        private String primaryPhone;
        private String secondaryPhone;
        private String email;
        private Address address;
        private String preferredContactMethod;
        private String preferredContactTime;
    }

    /**
     * Emergency contact
     */
    @Data
    public static class EmergencyContact {
        private String name;
        private String relationship;
        private String phone;
        private String email;
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
