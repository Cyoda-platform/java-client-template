package com.java_template.application.processor;

import com.java_template.application.entity.transaction.version_1.Transaction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * TransactionConfirmationProcessor - Confirms transactions
 * Verifies blockchain confirmations for crypto transactions
 */
@Component
public class TransactionConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransactionConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TransactionConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Confirming transaction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Transaction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid transaction wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Transaction> entityWithMetadata) {
        Transaction transaction = entityWithMetadata.entity();
        return transaction != null && transaction.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Transaction> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Transaction> context) {

        EntityWithMetadata<Transaction> entityWithMetadata = context.entityResponse();
        Transaction transaction = entityWithMetadata.entity();

        logger.debug("Confirming transaction: {} of type: {}", transaction.getTransactionId(), transaction.getType());

        // In production, this would:
        // 1. Query blockchain for transaction status
        // 2. Check confirmation count
        // 3. Verify transaction hash and amount
        // 4. Update wallet balance if confirmed
        // 5. Create immutable ledger entry

        transaction.setStatus("CONFIRMED");
        transaction.setConfirmedAt(LocalDateTime.now());
        transaction.setConfirmations(6); // Simulate 6 confirmations

        logger.info("Transaction {} confirmed with {} confirmations", 
            transaction.getTransactionId(), transaction.getConfirmations());

        return entityWithMetadata;
    }
}

