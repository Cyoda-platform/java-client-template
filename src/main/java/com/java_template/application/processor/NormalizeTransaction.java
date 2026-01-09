package com.java_template.application.processor;

import com.example.application.entity.transaction.version_1.Transaction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * NormalizeTransaction Processor - Transaction Monitoring Workflow
 * 
 * Normalizes transaction data from various sources into standard format.
 * Extracts and standardizes transaction fields for consistent processing.
 */
@Component
public class NormalizeTransaction implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeTransaction.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeTransaction(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Normalizing transaction for request: {}", request.getId());

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

        logger.debug("Normalizing transaction: {}", transaction.getId());

        // Normalize transaction fields
        normalizeTransactionData(transaction);

        // Create normalized payload
        String normalizedPayload = createNormalizedPayload(transaction);
        transaction.setNormalizedPayload(normalizedPayload);

        // Update metadata
        if (transaction.getMetadata() == null) {
            transaction.setMetadata(new HashMap<>());
        }
        transaction.getMetadata().put("normalized", true);
        transaction.getMetadata().put("normalizationTime", System.currentTimeMillis());

        logger.info("Transaction normalized: {}", transaction.getId());
        return entityWithMetadata;
    }

    private void normalizeTransactionData(Transaction transaction) {
        // Normalize currency to uppercase
        if (transaction.getCurrency() != null) {
            transaction.setCurrency(transaction.getCurrency().toUpperCase());
        }

        // Normalize channel
        if (transaction.getChannel() != null) {
            transaction.setChannel(transaction.getChannel().toUpperCase());
        }

        // Normalize merchant name
        if (transaction.getMerchant() != null) {
            transaction.setMerchant(transaction.getMerchant().trim());
        }

        // Ensure amount is positive
        if (transaction.getAmount() != null && transaction.getAmount().signum() < 0) {
            logger.warn("Negative amount detected for transaction: {}", transaction.getId());
        }

        // Normalize country code
        if (transaction.getCountry() != null) {
            transaction.setCountry(transaction.getCountry().toUpperCase());
        }

        // Initialize flags if null
        if (transaction.getFlags() == null) {
            transaction.setFlags(new java.util.ArrayList<>());
        }

        // Initialize matched watchlist IDs if null
        if (transaction.getMatchedWatchlistIds() == null) {
            transaction.setMatchedWatchlistIds(new java.util.ArrayList<>());
        }
    }

    private String createNormalizedPayload(Transaction transaction) {
        // Create a normalized JSON representation
        StringBuilder payload = new StringBuilder();
        payload.append("{");
        payload.append("\"id\":\"").append(transaction.getId()).append("\",");
        payload.append("\"accountId\":\"").append(transaction.getAccountId()).append("\",");
        payload.append("\"amount\":").append(transaction.getAmount()).append(",");
        payload.append("\"currency\":\"").append(transaction.getCurrency()).append("\",");
        payload.append("\"type\":\"").append(transaction.getType()).append("\",");
        payload.append("\"status\":\"").append(transaction.getStatus()).append("\",");
        payload.append("\"channel\":\"").append(transaction.getChannel()).append("\",");
        payload.append("\"country\":\"").append(transaction.getCountry()).append("\"");
        payload.append("}");
        return payload.toString();
    }
}

