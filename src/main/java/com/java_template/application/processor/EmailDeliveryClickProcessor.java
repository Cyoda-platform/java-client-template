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
 * EmailDeliveryClickProcessor - Handles email delivery click tracking
 * Transition: OPENED → CLICKED
 * 
 * Business Logic:
 * 1. Validate delivery was opened
 * 2. Set clicked timestamp
 * 3. Update delivery status to CLICKED
 * 4. Log click event with URL for analytics
 * 5. Update campaign engagement metrics
 */
@Component
public class EmailDeliveryClickProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryClickProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailDeliveryClickProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDelivery click for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "OPENED".equals(currentState);
    }

    /**
     * Main business logic processing method for email delivery click tracking
     */
    private EntityWithMetadata<EmailDelivery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailDelivery> context) {

        EntityWithMetadata<EmailDelivery> entityWithMetadata = context.entityResponse();
        EmailDelivery entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email delivery click: {} for {} in state: {}", 
                    entity.getId(), entity.getEmailAddress(), currentState);

        // Set clicked timestamp if not already set
        if (entity.getClickedDate() == null) {
            entity.setClickedDate(LocalDateTime.now());
        }

        // Update delivery status to CLICKED
        entity.setDeliveryStatus("CLICKED");

        logger.info("EmailDelivery {} clicked by {} at {}", 
                   entity.getId(), entity.getEmailAddress(), entity.getClickedDate());

        return entityWithMetadata;
    }
}
