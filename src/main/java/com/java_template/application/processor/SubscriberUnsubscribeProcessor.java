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
 * SubscriberUnsubscribeProcessor - Handles subscriber unsubscription from any state to UNSUBSCRIBED
 * Transition: PENDING/ACTIVE/INACTIVE → UNSUBSCRIBED
 * 
 * Business Logic:
 * 1. Validate subscriber exists
 * 2. Set isActive to false
 * 3. Log unsubscribe reason and timestamp
 * 4. Remove from future email campaigns
 */
@Component
public class SubscriberUnsubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberUnsubscribeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberUnsubscribeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber unsubscribe for request: {}", request.getId());

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
        
        // Allow unsubscribe from PENDING, ACTIVE, or INACTIVE states
        boolean validState = "PENDING".equals(currentState) || "ACTIVE".equals(currentState) || "INACTIVE".equals(currentState);
        
        return entity != null && entity.isValid() && technicalId != null && validState;
    }

    /**
     * Main business logic processing method for subscriber unsubscription
     */
    private EntityWithMetadata<Subscriber> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing subscriber unsubscribe: {} in state: {}", entity.getEmail(), currentState);

        // Set isActive to false
        entity.setIsActive(false);

        logger.info("Subscriber {} unsubscribed successfully", entity.getEmail());

        return entityWithMetadata;
    }
}
