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
 * ReactivateOwnerProcessor - Reactivates suspended owner account
 * 
 * This processor handles the reactivation of a suspended owner account,
 * restoring their ability to use the system.
 */
@Component
public class ReactivateOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReactivateOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReactivateOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Reactivating Owner for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null && "suspended".equals(currentState);
    }

    /**
     * Main business logic for reactivating an owner
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Reactivating owner: {} {} with ID: {}", 
                    owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Log reactivation activity
        logger.info("Owner {} {} (ID: {}) has been reactivated", 
                   owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Note: The owner entity itself doesn't need modification for reactivation
        // The state transition to 'active' is handled by the workflow
        // Any suspension data clearing would be done through separate processes

        return entityWithMetadata;
    }
}
