package com.java_template.application.processor;

import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
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
 * EmailDeliveryOpenProcessor - Handles email delivery open tracking
 * Transition: DELIVERED → OPENED
 * 
 * Business Logic:
 * 1. Validate delivery was successfully delivered
 * 2. Set opened timestamp
 * 3. Update delivery status to OPENED
 * 4. Log open event for analytics
 * 5. Update campaign engagement metrics
 */
@Component
public class EmailDeliveryOpenProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryOpenProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailDeliveryOpenProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDelivery open for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailDelivery.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email delivery entity")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailDelivery> entityWithMetadata) {
        EmailDelivery entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        return entity != null && entity.isValid() && technicalId != null && "DELIVERED".equals(currentState);
    }

    /**
     * Main business logic processing method for email delivery open tracking
     */
    private EntityWithMetadata<EmailDelivery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailDelivery> context) {

        EntityWithMetadata<EmailDelivery> entityWithMetadata = context.entityResponse();
        EmailDelivery entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email delivery open: {} for {} in state: {}", 
                    entity.getId(), entity.getEmailAddress(), currentState);

        // Set opened timestamp if not already set
        if (entity.getOpenedDate() == null) {
            entity.setOpenedDate(LocalDateTime.now());
        }

        // Update delivery status to OPENED
        entity.setDeliveryStatus("OPENED");

        logger.info("EmailDelivery {} opened by {} at {}", 
                   entity.getId(), entity.getEmailAddress(), entity.getOpenedDate());

        return entityWithMetadata;
    }
}
