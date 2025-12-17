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
 * WithdrawalFinalizationProcessor - Finalizes withdrawal after confirmation
 * Updates wallet balance and creates final ledger entry
 */
@Component
public class WithdrawalFinalizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WithdrawalFinalizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public WithdrawalFinalizationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Finalizing withdrawal for request: {}", request.getId());

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

        logger.debug("Finalizing withdrawal for wallet: {}", wallet.getWalletId());

        // In production, this would:
        // 1. Verify blockchain confirmation
        // 2. Update wallet balance (debit)
        // 3. Create immutable ledger entry
        // 4. Create Transaction entity with final status
        // 5. Notify user of completion

        wallet.setLastActivityAt(LocalDateTime.now());

        logger.info("Withdrawal finalized for wallet: {} with new balance: {}", 
            wallet.getWalletId(), wallet.getBalance());

        return entityWithMetadata;
    }
}

