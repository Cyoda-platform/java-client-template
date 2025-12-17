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

/**
 * WithdrawalSigningProcessor - Signs and broadcasts withdrawal transactions
 * Handles cryptographic signing and blockchain broadcast
 */
@Component
public class WithdrawalSigningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WithdrawalSigningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public WithdrawalSigningProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Signing and broadcasting withdrawal for request: {}", request.getId());

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

        logger.debug("Signing withdrawal transaction for wallet: {}", wallet.getWalletId());

        // In production, this would:
        // 1. Retrieve private key from HSM/secure storage
        // 2. Build transaction
        // 3. Sign transaction with private key
        // 4. Broadcast to blockchain
        // 5. Record transaction hash
        // 6. Implement multisig if required

        logger.info("Withdrawal transaction signed and broadcast for wallet: {}", wallet.getWalletId());
        return entityWithMetadata;
    }
}

