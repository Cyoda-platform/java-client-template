package com.java_template.application.processor;

import com.java_template.application.entity.wallet.version_1.Wallet;
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
 * DepositDetectionProcessor - Detects external deposits to wallet addresses
 * Monitors blockchain for incoming transactions
 */
@Component
public class DepositDetectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DepositDetectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DepositDetectionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Detecting deposit for wallet request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Wallet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid wallet wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Wallet> entityWithMetadata) {
        Wallet wallet = entityWithMetadata.entity();
        return wallet != null && wallet.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Wallet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Wallet> context) {

        EntityWithMetadata<Wallet> entityWithMetadata = context.entityResponse();
        Wallet wallet = entityWithMetadata.entity();

        logger.debug("Checking for deposits to wallet: {} at address: {}", wallet.getWalletId(), wallet.getAddress());

        // In production, this would:
        // 1. Query blockchain for transactions to wallet address
        // 2. Verify confirmations
        // 3. Create Transaction entity
        // 4. Update wallet balance (pending)

        wallet.setLastActivityAt(LocalDateTime.now());

        logger.info("Deposit detection completed for wallet: {}", wallet.getWalletId());
        return entityWithMetadata;
    }
}

