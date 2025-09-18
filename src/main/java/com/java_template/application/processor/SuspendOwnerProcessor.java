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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SuspendOwnerProcessor - Suspends owner account
 * 
 * This processor handles the suspension of an owner account,
 * logging the suspension activity for tracking purposes.
 */
@Component
public class SuspendOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SuspendOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Suspending Owner for request: {}", request.getId());

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
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "active".equals(currentState);
    }

    /**
     * Main business logic for suspending an owner
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Suspending owner: {} {} with ID: {}", 
                    owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Log suspension activity
        logger.info("Owner {} {} (ID: {}) has been suspended", 
                   owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Note: Suspension details (reason, date) would be tracked in metadata or separate entities
        // This processor focuses on the owner-specific aspects of the suspension

        return entityWithMetadata;
    }
}
