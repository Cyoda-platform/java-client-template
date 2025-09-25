package com.java_template.application.entity.investigator.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Investigator Entity for Clinical Trial Management
 * 
 * Represents a clinical investigator with credentials, qualifications,
 * site assignments, and regulatory information.
 */
@Data
public class Investigator implements CyodaEntity {
    public static final String ENTITY_NAME = Investigator.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String investigatorId;
    
    // Personal information
    private String firstName;
    private String lastName;
    private String middleName;
    private String title; // Dr., Prof., etc.
    private String suffix; // MD, PhD, etc.
    private LocalDate dateOfBirth;
    private String gender;
    
    // Professional information
    private String medicalLicenseNumber;
    private String licenseState;
    private LocalDate licenseExpirationDate;
    private String specialty;
    private String subSpecialty;
    private Integer yearsOfExperience;
    
    // Contact information
    private ContactInfo contact;
    
    // Qualifications and certifications
    private List<Education> education;
    private List<Certification> certifications;
    private List<Training> trainings;
    
    // Site assignments
    private List<SiteAssignment> siteAssignments;
    
    // Study assignments
    private List<StudyAssignment> studyAssignments;
    
    // Regulatory information
    private List<RegulatoryApproval> regulatoryApprovals;
    private String form1572Status; // FDA Form 1572 status
    private LocalDate form1572Date;
    
    // Performance metrics
    private InvestigatorPerformance performance;
    
    // Financial disclosure
    private FinancialDisclosure financialDisclosure;
    
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
        return investigatorId != null && !investigatorId.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               medicalLicenseNumber != null && !medicalLicenseNumber.trim().isEmpty();
    }

    /**
     * Contact information
     */
    @Data
    public static class ContactInfo {
        private String email;
        private String primaryPhone;
        private String secondaryPhone;
        private String mobile;
        private String fax;
        private Address address;
        private String preferredContactMethod;
    }

    /**
     * Education information
     */
    @Data
    public static class Education {
        private String institution;
        private String degree; // MD, PhD, etc.
        private String fieldOfStudy;
        private LocalDate graduationDate;
        private String country;
        private Boolean verified;
    }

    /**
     * Certification information
     */
    @Data
    public static class Certification {
        private String certificationType; // Board Certification, GCP, etc.
        private String certifyingBody;
        private String certificateNumber;
        private LocalDate issueDate;
        private LocalDate expirationDate;
        private String status; // Active, Expired, Suspended
        private Boolean verified;
    }

    /**
     * Training information
     */
    @Data
    public static class Training {
        private String trainingType; // GCP, Protocol Specific, etc.
        private String trainingProvider;
        private LocalDate completionDate;
        private LocalDate expirationDate;
        private String certificateNumber;
        private Boolean verified;
    }

    /**
     * Site assignment information
     */
    @Data
    public static class SiteAssignment {
        private String siteId;
        private String siteName;
        private String role; // Principal Investigator, Sub-Investigator, etc.
        private LocalDate assignmentDate;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status; // Active, Inactive, Suspended
        private String responsibilities;
    }

    /**
     * Study assignment information
     */
    @Data
    public static class StudyAssignment {
        private String studyId;
        private String studyTitle;
        private String siteId;
        private String role; // Principal Investigator, Co-Investigator, etc.
        private LocalDate assignmentDate;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status; // Active, Completed, Withdrawn
        private Integer participantsEnrolled;
        private String delegationLog;
    }

    /**
     * Regulatory approval information
     */
    @Data
    public static class RegulatoryApproval {
        private String authority; // IRB, Ethics Committee
        private String approvalNumber;
        private LocalDate approvalDate;
        private LocalDate expirationDate;
        private String status; // Active, Expired, Suspended
        private String studyId;
        private String siteId;
    }

    /**
     * Performance metrics
     */
    @Data
    public static class InvestigatorPerformance {
        private Integer totalStudiesCompleted;
        private Integer totalParticipantsEnrolled;
        private Double averageEnrollmentRate; // participants per month
        private Double protocolComplianceRate; // percentage
        private Double dataQualityScore; // percentage
        private Integer protocolDeviations;
        private Integer seriousAdverseEvents;
        private String performanceRating; // Excellent, Good, Satisfactory, Needs Improvement
        private LocalDate lastPerformanceReview;
    }

    /**
     * Financial disclosure information
     */
    @Data
    public static class FinancialDisclosure {
        private Boolean hasFinancialInterest;
        private String financialInterestDetails;
        private Boolean hasEquityInterest;
        private String equityInterestDetails;
        private Boolean hasProprietaryInterest;
        private String proprietaryInterestDetails;
        private Boolean hasSignificantPayments;
        private String significantPaymentsDetails;
        private LocalDate disclosureDate;
        private String disclosureStatus; // Complete, Pending, Overdue
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
