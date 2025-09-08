package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.UUID;

/**
 * SubscriberDeactivationProcessor - Handles subscriber deactivation from ACTIVE to INACTIVE
 * Transition: ACTIVE → INACTIVE
 * 
 * Business Logic:
 * 1. Validate subscriber exists and is in ACTIVE state
 * 2. Set isActive to false
 * 3. Log deactivation reason and timestamp
 */
@Component
public class SubscriberDeactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberDeactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberDeactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber deactivation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscriber entity")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        return entity != null && entity.isValid() && technicalId != null && "ACTIVE".equals(currentState);
    }

    /**
     * Main business logic processing method for subscriber deactivation
     */
    private EntityWithMetadata<Subscriber> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing subscriber deactivation: {} in state: {}", entity.getEmail(), currentState);

        // Set isActive to false
        entity.setIsActive(false);

        logger.info("Subscriber {} deactivated successfully", entity.getEmail());

        return entityWithMetadata;
    }
}
