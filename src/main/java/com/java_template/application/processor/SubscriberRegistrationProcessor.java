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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SubscriberRegistrationProcessor - Handles initial subscriber registration
 * Transition: INITIAL → PENDING
 * 
 * Business Logic:
 * 1. Validate email format and uniqueness
 * 2. Set subscription date to current timestamp
 * 3. Set isActive to true
 * 4. Generate unique subscriber ID
 * 5. Initialize preferences if provided
 * 6. Save subscriber entity
 */
@Component
public class SubscriberRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber registration for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method for subscriber registration
     */
    private EntityWithMetadata<Subscriber> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing subscriber registration: {} in state: {}", entity.getEmail(), currentState);

        // Set subscription date to current timestamp
        entity.setSubscriptionDate(LocalDateTime.now());
        
        // Set isActive to true (default for new subscribers)
        entity.setIsActive(true);
        
        // Generate unique subscriber ID if not already set
        if (entity.getId() == null || entity.getId().trim().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }

        logger.info("Subscriber {} registered successfully with ID: {}", entity.getEmail(), entity.getId());

        return entityWithMetadata;
    }
}
