package com.java_template.application.entity.site.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Site Entity for Clinical Trial Management
 * 
 * Represents a research site participating in clinical studies,
 * including location, staff, capabilities, and performance metrics.
 */
@Data
public class Site implements CyodaEntity {
    public static final String ENTITY_NAME = Site.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String siteId;
    
    // Basic site information
    private String siteName;
    private String siteNumber;
    private String siteType; // Academic, Private Practice, Hospital, etc.
    private String institutionName;
    private String department;
    
    // Location information
    private Address address;
    private String timeZone;
    private String country;
    private String region;
    
    // Contact information
    private List<SiteContact> contacts;
    private SiteContact primaryContact;
    
    // Study assignments
    private List<StudyAssignment> studyAssignments;
    
    // Site capabilities
    private SiteCapabilities capabilities;
    
    // Regulatory information
    private List<RegulatoryApproval> regulatoryApprovals;
    private List<Certification> certifications;
    
    // Performance metrics
    private SitePerformance performance;
    
    // Financial information
    private FinancialInfo financialInfo;
    
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
        return siteId != null && !siteId.trim().isEmpty() &&
               siteName != null && !siteName.trim().isEmpty() &&
               address != null;
    }

    /**
     * Site contact information
     */
    @Data
    public static class SiteContact {
        private String contactId;
        private String name;
        private String role; // Principal Investigator, Study Coordinator, Site Manager
        private String title;
        private String email;
        private String phone;
        private String mobile;
        private String fax;
        private Boolean isPrimary;
        private List<String> responsibilities;
        private String availability;
    }

    /**
     * Study assignment information
     */
    @Data
    public static class StudyAssignment {
        private String studyId;
        private String studyTitle;
        private String principalInvestigatorId;
        private LocalDate assignmentDate;
        private LocalDate activationDate;
        private String assignmentStatus; // Assigned, Active, Suspended, Closed
        private Integer targetEnrollment;
        private Integer actualEnrollment;
        private LocalDate firstPatientIn;
        private LocalDate lastPatientOut;
    }

    /**
     * Site capabilities and resources
     */
    @Data
    public static class SiteCapabilities {
        private List<String> therapeuticAreas;
        private List<String> studyPhases; // Phase I, II, III, IV
        private List<String> studyTypes; // Interventional, Observational
        private Integer maxConcurrentStudies;
        private Integer maxParticipantsPerStudy;
        private List<String> specialEquipment;
        private List<String> laboratoryServices;
        private List<String> imagingCapabilities;
        private Boolean hasPharmacy;
        private Boolean hasDataManagement;
        private Boolean has24HourCoverage;
        private List<String> languages;
    }

    /**
     * Regulatory approval information
     */
    @Data
    public static class RegulatoryApproval {
        private String authority; // IRB, Ethics Committee, Regulatory Agency
        private String approvalNumber;
        private LocalDate approvalDate;
        private LocalDate expirationDate;
        private String status; // Active, Expired, Suspended
        private String scope;
    }

    /**
     * Certification information
     */
    @Data
    public static class Certification {
        private String certificationType; // GCP, ISO, AAHRPP
        private String certificationBody;
        private String certificateNumber;
        private LocalDate issueDate;
        private LocalDate expirationDate;
        private String status; // Valid, Expired, Suspended
    }

    /**
     * Site performance metrics
     */
    @Data
    public static class SitePerformance {
        private Double enrollmentRate; // participants per month
        private Double retentionRate; // percentage
        private Double protocolDeviationRate; // percentage
        private Double queryRate; // queries per participant
                private Integer totalStudiesCompleted;
        private Integer totalParticipantsEnrolled;
        private Double averageEnrollmentTime; // days
        private Double dataQualityScore; // percentage
        private LocalDate lastMonitoringVisit;
        private String performanceRating; // Excellent, Good, Satisfactory, Needs Improvement
    }

    /**
     * Financial information
     */
    @Data
    public static class FinancialInfo {
        private String paymentTerms;
        private String currency;
        private String bankingDetails;
        private String taxId;
        private String billingContact;
        private String billingAddress;
        private List<String> acceptedPaymentMethods;
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
        private Double latitude;
        private Double longitude;
    }
}
