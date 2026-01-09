package com.example.application.processor;

import com.example.application.entity.transaction.version_1.Transaction;
import com.example.application.entity.alert.version_1.Alert;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CreateAlert Processor - Transaction Monitoring Workflow
 * 
 * Creates compliance alerts for high-risk transactions.
 * Generates alert records with appropriate severity levels.
 */
@Component
public class CreateAlert implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAlert.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateAlert(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Creating alert for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Transaction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid transaction entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Transaction> entityWithMetadata) {
        Transaction entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Transaction> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Transaction> context) {

        EntityWithMetadata<Transaction> entityWithMetadata = context.entityResponse();
        Transaction transaction = entityWithMetadata.entity();

        logger.debug("Creating alert for transaction: {}", transaction.getId());

        // Create alert for high-risk transaction
        if (transaction.getScore() != null && transaction.getScore() >= 75) {
            createComplianceAlert(transaction);
        }

        return entityWithMetadata;
    }

    private void createComplianceAlert(Transaction transaction) {
        try {
            Alert alert = new Alert();
            alert.setId("ALERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            alert.setTransactionId(transaction.getId());
            alert.setCustomerId(transaction.getAccountId()); // Use account ID as customer reference
            alert.setAlertType(determineAlertType(transaction));
            alert.setSeverity(determineSeverity(transaction.getScore()));
            alert.setStatus(Alert.Status.OPEN);
            alert.setCreatedAt(LocalDateTime.now());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("transactionAmount", transaction.getAmount());
            metadata.put("transactionCurrency", transaction.getCurrency());
            metadata.put("riskScore", transaction.getScore());
            metadata.put("flags", transaction.getFlags());
            metadata.put("watchlistMatches", transaction.getMatchedWatchlistIds());
            metadata.put("country", transaction.getCountry());
            metadata.put("channel", transaction.getChannel());
            alert.setMetadata(metadata);

            entityService.create(alert);
            logger.info("Alert created: {} for transaction: {}", alert.getId(), transaction.getId());

            // Update transaction metadata with alert reference
            if (transaction.getMetadata() == null) {
                transaction.setMetadata(new HashMap<>());
            }
            transaction.getMetadata().put("alertId", alert.getId());
        } catch (Exception e) {
            logger.error("Failed to create alert for transaction: {}", transaction.getId(), e);
        }
    }

    private String determineAlertType(Transaction transaction) {
        if (transaction.getMatchedWatchlistIds() != null && !transaction.getMatchedWatchlistIds().isEmpty()) {
            return "WATCHLIST_HIT";
        }
        if (transaction.getFlags() != null && transaction.getFlags().contains("VELOCITY_EXCEEDED")) {
            return "VELOCITY_EXCEEDED";
        }
        if (transaction.getFlags() != null && transaction.getFlags().contains("LARGE_AMOUNT")) {
            return "LARGE_TRANSACTION";
        }
        return "UNUSUAL_ACTIVITY";
    }

    private Alert.Severity determineSeverity(Double score) {
        if (score >= 90) return Alert.Severity.HIGH;
        if (score >= 75) return Alert.Severity.MEDIUM;
        return Alert.Severity.LOW;
    }
}

