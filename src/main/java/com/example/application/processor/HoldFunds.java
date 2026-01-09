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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HoldFunds Processor - Transaction Monitoring Workflow
 * 
 * Holds funds for suspicious transactions pending compliance review.
 * Updates transaction status and creates hold records.
 */
@Component
public class HoldFunds implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HoldFunds.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HoldFunds(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Holding funds for request: {}", request.getId());

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

        logger.debug("Holding funds for transaction: {}", transaction.getId());

        // Keep transaction status as PENDING (hold is tracked in metadata/flags)
        transaction.setStatus(Transaction.TransactionStatus.PENDING);

        // Update metadata with hold information
        if (transaction.getMetadata() == null) {
            transaction.setMetadata(new HashMap<>());
        }
        transaction.getMetadata().put("held", true);
        transaction.getMetadata().put("holdTime", LocalDateTime.now().toString());
        transaction.getMetadata().put("holdReason", "Compliance review required - high risk score");
        transaction.getMetadata().put("holdAmount", transaction.getAmount());

        // Add hold flag
        if (transaction.getFlags() == null) {
            transaction.setFlags(new java.util.ArrayList<>());
        }
        if (!transaction.getFlags().contains("FUNDS_HELD")) {
            transaction.getFlags().add("FUNDS_HELD");
        }

        logger.info("Funds held for transaction: {} with amount: {}", transaction.getId(), transaction.getAmount());
        return entityWithMetadata;
    }
}

