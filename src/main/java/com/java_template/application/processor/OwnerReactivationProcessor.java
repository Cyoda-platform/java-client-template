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
 * OwnerReactivationProcessor - Handles owner reactivation business logic
 * 
 * Reactivates a suspended owner account, including:
 * - Validating reactivation eligibility
 * - Restoring account access
 * - Allowing owner to place new orders and register pets
 */
@Component
public class OwnerReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Owner.class)
            .validate(this::isValidEntityWithMetadata, "Invalid owner reactivation data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return owner != null && owner.isValid() && "SUSPENDED".equals(currentState);
    }

    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {
        
        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing owner reactivation for: {} in state: {}", owner.getOwnerId(), currentState);

        // 1. Validate reactivation eligibility (basic validation already done in isValid())
        // Additional reactivation logic could be added here if needed

        // 2. Owner can place new orders and register new pets again
        // 3. Pets remain in their current states (owner can manually reactivate as needed)
        
        logger.info("Owner {} reactivated successfully", owner.getOwnerId());

        return entityWithMetadata;
    }
}
