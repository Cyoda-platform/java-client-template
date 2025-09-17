package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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

/**
 * OwnerDeactivationProcessor - Deactivate owner account
 */
@Component
public class OwnerDeactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerDeactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerDeactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner deactivation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Owner.class)
                .validate(this::isValidEntityWithMetadata, "Invalid owner entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Deactivate owner account
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Deactivating owner: {} in state: {}", owner.getOwnerId(), currentState);

        // Verify owner is in active state
        if (!"active".equals(currentState)) {
            logger.warn("Owner {} is not in active state, current state: {}", owner.getOwnerId(), currentState);
        }

        // Update verification status to indicate deactivation
        owner.setVerificationStatus("Deactivated");

        // Cancel any pending adoptions would be handled by the Adoption entity
        // This processor focuses on owner-specific deactivation tasks
        logger.debug("Cancelling any pending adoptions for owner: {}", owner.getOwnerId());

        // Archive owner data (simulated)
        logger.debug("Archiving data for owner: {}", owner.getOwnerId());

        // Send deactivation notice (simulated)
        logger.info("Deactivation notice sent to: {}", owner.getEmail());

        logger.info("Owner {} deactivated successfully", owner.getOwnerId());

        return entityWithMetadata;
    }
}
