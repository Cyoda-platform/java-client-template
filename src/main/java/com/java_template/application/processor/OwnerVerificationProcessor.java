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
 * OwnerVerificationProcessor - Handles owner verification business logic
 * 
 * Activates owner account after verification, including:
 * - Validating verification criteria
 * - Making owner eligible to register pets and place orders
 */
@Component
public class OwnerVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerVerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner verification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Owner.class)
            .validate(this::isValidEntityWithMetadata, "Invalid owner verification data")
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
        return owner != null && owner.isValid() && "PENDING".equals(currentState);
    }

    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {
        
        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing owner verification for: {} in state: {}", owner.getOwnerId(), currentState);

        // 1. Validate verification criteria (basic validation already done in isValid())
        // Additional verification logic could be added here if needed

        // 2. Owner becomes eligible to register pets and place orders
        logger.info("Owner {} verified and activated successfully", owner.getOwnerId());

        return entityWithMetadata;
    }
}
