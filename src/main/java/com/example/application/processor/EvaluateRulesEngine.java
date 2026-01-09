package com.example.application.processor;

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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * EvaluateRulesEngine Processor - Transaction Monitoring Workflow
 * 
 * Evaluates transaction against compliance rules engine.
 * Identifies rule violations and flags suspicious transactions.
 */
@Component
public class EvaluateRulesEngine implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EvaluateRulesEngine.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EvaluateRulesEngine(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Evaluating rules for transaction request: {}", request.getId());

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

        logger.debug("Evaluating rules for transaction: {}", transaction.getId());

        // Evaluate transaction against rules
        evaluateComplianceRules(transaction);

        // Update metadata with rule evaluation results
        if (transaction.getMetadata() == null) {
            transaction.setMetadata(new HashMap<>());
        }
        transaction.getMetadata().put("rulesEvaluated", true);
        transaction.getMetadata().put("evaluationTime", System.currentTimeMillis());

        logger.info("Rules evaluation completed for transaction: {}", transaction.getId());
        return entityWithMetadata;
    }

    private void evaluateComplianceRules(Transaction transaction) {
        // Rule 1: Large transaction amount
        if (transaction.getAmount() != null && transaction.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            transaction.getFlags().add("LARGE_AMOUNT");
            logger.debug("Transaction {} flagged: LARGE_AMOUNT", transaction.getId());
        }

        // Rule 2: High-risk country
        if (isHighRiskCountry(transaction.getCountry())) {
            transaction.getFlags().add("HIGH_RISK_COUNTRY");
            logger.debug("Transaction {} flagged: HIGH_RISK_COUNTRY", transaction.getId());
        }

        // Rule 3: Unusual channel
        if (isUnusualChannel(transaction.getChannel())) {
            transaction.getFlags().add("UNUSUAL_CHANNEL");
            logger.debug("Transaction {} flagged: UNUSUAL_CHANNEL", transaction.getId());
        }

        // Rule 4: Rapid transactions (velocity check)
        if (transaction.getMetadata() != null && transaction.getMetadata().containsKey("transactionCount")) {
            int count = (Integer) transaction.getMetadata().get("transactionCount");
            if (count > 5) {
                transaction.getFlags().add("VELOCITY_EXCEEDED");
                logger.debug("Transaction {} flagged: VELOCITY_EXCEEDED", transaction.getId());
            }
        }

        // Rule 5: Specific transaction type checks
        if (transaction.getType() == Transaction.TransactionType.TRANSFER) {
            if (transaction.getFromAccountId() == null || transaction.getToAccountId() == null) {
                transaction.getFlags().add("INVALID_TRANSFER");
                logger.debug("Transaction {} flagged: INVALID_TRANSFER", transaction.getId());
            }
        }
    }

    private boolean isHighRiskCountry(String country) {
        java.util.Set<String> highRiskCountries = java.util.Set.of("KP", "IR", "SY", "CU");
        return country != null && highRiskCountries.contains(country.toUpperCase());
    }

    private boolean isUnusualChannel(String channel) {
        java.util.Set<String> unusualChannels = java.util.Set.of("CRYPTO", "WIRE_TRANSFER", "CASH");
        return channel != null && unusualChannels.contains(channel.toUpperCase());
    }
}

