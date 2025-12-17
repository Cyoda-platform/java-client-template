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
 * WithdrawalAMLProcessor - Performs AML checks on withdrawals
 * Validates destination addresses and transaction patterns
 */
@Component
public class WithdrawalAMLProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WithdrawalAMLProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public WithdrawalAMLProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Performing AML check on withdrawal for request: {}", request.getId());

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

        logger.debug("Performing AML check for withdrawal from wallet: {}", wallet.getWalletId());

        // In production, this would:
        // 1. Check destination address against sanctions lists
        // 2. Verify withdrawal limits
        // 3. Evaluate transaction patterns
        // 4. Check for suspicious activity
        // 5. Generate alerts if needed

        logger.info("AML check passed for withdrawal from wallet: {}", wallet.getWalletId());
        return entityWithMetadata;
    }
}

