package com.example.application.processor;

import com.example.application.entity.fraud_detection.version_1.FraudDetection;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * FraudDetectionProcessor - Analyzes transactions for fraud risk
 * Applies device fingerprinting, velocity checks, IP geolocation, and blacklist checks
 */
@Component
public class FraudDetectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Configurable thresholds
    private static final Double HIGH_RISK_THRESHOLD = 0.7;
    private static final Double MEDIUM_RISK_THRESHOLD = 0.4;

    public FraudDetectionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FraudDetection for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(FraudDetection.class)
                .validate(this::isValidEntityWithMetadata, "Invalid fraud detection")
                .map(this::processFraudAnalysisLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<FraudDetection> entityWithMetadata) {
        FraudDetection entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<FraudDetection> processFraudAnalysisLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<FraudDetection> context) {

        EntityWithMetadata<FraudDetection> entityWithMetadata = context.entityResponse();
        FraudDetection fraudDetection = entityWithMetadata.entity();

        logger.debug("Analyzing fraud risk for transaction: {}", fraudDetection.getTransactionId());

        // Calculate fraud score based on multiple factors
        calculateFraudScore(fraudDetection);

        // Determine risk level
        determineRiskLevel(fraudDetection);

        // Make decision
        makeDecision(fraudDetection);

        // Update timestamps
        fraudDetection.setUpdatedAt(LocalDateTime.now());
        if (fraudDetection.getCreatedAt() == null) {
            fraudDetection.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Fraud analysis completed for transaction: {} - Decision: {}", 
                   fraudDetection.getTransactionId(), fraudDetection.getDecision());
        return entityWithMetadata;
    }

    private void calculateFraudScore(FraudDetection fraudDetection) {
        Double score = 0.0;

        // Velocity check (0.0 - 0.3)
        if (Boolean.TRUE.equals(fraudDetection.getVelocityThresholdExceeded())) {
            score += 0.3;
        }

        // Impossible travel (0.0 - 0.25)
        if (Boolean.TRUE.equals(fraudDetection.getImpossibleTravel())) {
            score += 0.25;
        }

        // Blacklist checks (0.0 - 0.25)
        int blacklistCount = 0;
        if (Boolean.TRUE.equals(fraudDetection.getCardBlacklisted())) blacklistCount++;
        if (Boolean.TRUE.equals(fraudDetection.getIpBlacklisted())) blacklistCount++;
        if (Boolean.TRUE.equals(fraudDetection.getDeviceBlacklisted())) blacklistCount++;
        if (Boolean.TRUE.equals(fraudDetection.getCustomerBlacklisted())) blacklistCount++;
        score += (blacklistCount * 0.0625);

        // Device fingerprint anomaly (0.0 - 0.2)
        if (fraudDetection.getDeviceFingerprint() != null && fraudDetection.getDeviceFingerprint().contains("anomaly")) {
            score += 0.2;
        }

        fraudDetection.setFraudScore(Math.min(score, 1.0));
        logger.debug("Fraud score calculated: {} for transaction: {}", score, fraudDetection.getTransactionId());
    }

    private void determineRiskLevel(FraudDetection fraudDetection) {
        Double score = fraudDetection.getFraudScore();
        if (score >= HIGH_RISK_THRESHOLD) {
            fraudDetection.setRiskLevel("CRITICAL");
        } else if (score >= MEDIUM_RISK_THRESHOLD) {
            fraudDetection.setRiskLevel("HIGH");
        } else if (score >= 0.2) {
            fraudDetection.setRiskLevel("MEDIUM");
        } else {
            fraudDetection.setRiskLevel("LOW");
        }
    }

    private void makeDecision(FraudDetection fraudDetection) {
        String riskLevel = fraudDetection.getRiskLevel();
        fraudDetection.setDecision(switch (riskLevel) {
            case "CRITICAL" -> "BLOCK";
            case "HIGH" -> "REVIEW";
            case "MEDIUM" -> "REVIEW";
            default -> "APPROVE";
        });

        fraudDetection.setDecisionReason("Decision based on risk level: " + riskLevel);
    }
}

