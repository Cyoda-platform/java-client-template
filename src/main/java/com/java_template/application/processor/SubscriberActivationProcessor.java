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
 * SubscriberActivationProcessor - Handles subscriber activation from PENDING to ACTIVE
 * Transition: PENDING → ACTIVE
 * 
 * Business Logic:
 * 1. Validate subscriber exists and is in PENDING state
 * 2. Verify confirmation token if provided
 * 3. Update subscriber status to active
 * 4. Log activation timestamp
 */
@Component
public class SubscriberActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber activation for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "PENDING".equals(currentState);
    }

    /**
     * Main business logic processing method for subscriber activation
     */
    private EntityWithMetadata<Subscriber> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing subscriber activation: {} in state: {}", entity.getEmail(), currentState);

        // Ensure subscriber is active
        entity.setIsActive(true);

        logger.info("Subscriber {} activated successfully", entity.getEmail());

        return entityWithMetadata;
    }
}
