package com.java_template.application.entity.kycprofile.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * KYCProfile Entity - Represents KYC/AML compliance data for a user
 * Tracks identity verification, documents, and compliance status
 */
@Data
public class KYCProfile implements CyodaEntity {
    public static final String ENTITY_NAME = "KYCProfile";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String kycProfileId;

    // Reference to user
    private String userId;

    // KYC status
    private String status; // "PENDING", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "SUSPENDED"

    // Verification level
    private String verificationLevel; // "BASIC", "INTERMEDIATE", "ADVANCED"

    // Document information
    private List<Document> documents;

    // Screening results
    private String sanctionsScreeningStatus; // "PENDING", "PASSED", "FLAGGED", "BLOCKED"
    private String pepScreeningStatus; // "PENDING", "PASSED", "FLAGGED"

    // Verification notes
    private String verificationNotes;
    private String rejectionReason;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return kycProfileId != null && !kycProfileId.isBlank() &&
               userId != null && !userId.isBlank();
    }

    @Data
    public static class Document {
        private String documentId;
        private String type; // "PASSPORT", "DRIVER_LICENSE", "ID_CARD", "PROOF_OF_ADDRESS"
        private String status; // "PENDING", "VERIFIED", "REJECTED"
        private String url;
        private LocalDateTime uploadedAt;
        private LocalDateTime verifiedAt;
    }
}

