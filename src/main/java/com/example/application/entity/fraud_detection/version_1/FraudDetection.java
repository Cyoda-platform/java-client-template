package com.example.application.entity.fraud_detection.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FraudDetection Entity - Fraud scoring and decision logging
 * Integrates device fingerprinting, velocity checks, IP geolocation, and blacklist checks
 */
@Data
public class FraudDetection implements CyodaEntity {
    public static final String ENTITY_NAME = "FraudDetection";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String fraudDetectionId;

    // Reference to transaction
    private String transactionId;
    private String merchantId;
    private String customerId;

    // Fraud scoring
    private Double fraudScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String decision; // APPROVE, REVIEW, BLOCK

    // Device fingerprinting
    private String deviceFingerprint;
    private String deviceType;
    private String osType;
    private String browserType;

    // Velocity checks
    private Integer transactionCountLast24h;
    private Integer transactionCountLastHour;
    private Double totalAmountLast24h;
    private Boolean velocityThresholdExceeded;

    // IP geolocation
    private String ipAddress;
    private String country;
    private String city;
    private Double latitude;
    private Double longitude;
    private Boolean impossibleTravel;

    // Blacklist checks
    private Boolean cardBlacklisted;
    private Boolean ipBlacklisted;
    private Boolean deviceBlacklisted;
    private Boolean customerBlacklisted;

    // Decision rationale
    private String decisionReason;
    private List<RuleMatch> matchedRules;
    private String reviewStatus; // PENDING, APPROVED, REJECTED
    private String reviewedBy;
    private LocalDateTime reviewedAt;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec()
                .withName(ENTITY_NAME)
                .withVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return fraudDetectionId != null && !fraudDetectionId.isBlank() &&
               transactionId != null && !transactionId.isBlank() &&
               merchantId != null && !merchantId.isBlank() &&
               fraudScore != null && fraudScore >= 0.0 && fraudScore <= 1.0 &&
               decision != null && !decision.isBlank();
    }

    @Data
    public static class RuleMatch {
        private String ruleName;
        private String ruleType; // VELOCITY, GEOLOCATION, BLACKLIST, DEVICE, etc.
        private Double ruleScore;
        private String ruleReason;
    }
}

