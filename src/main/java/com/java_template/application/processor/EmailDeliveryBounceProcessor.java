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

import java.util.UUID;

/**
 * EmailDeliveryBounceProcessor - Handles email delivery bounce
 * Transition: SENT → BOUNCED
 * 
 * Business Logic:
 * 1. Log bounce reason and timestamp
 * 2. Set error message with bounce details
 * 3. Update delivery status to BOUNCED
 * 4. Mark subscriber email as potentially invalid
 * 5. Increment campaign failure counter
 */
@Component
public class EmailDeliveryBounceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryBounceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailDeliveryBounceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDelivery bounce for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "SENT".equals(currentState);
    }

    /**
     * Main business logic processing method for email delivery bounce
     */
    private EntityWithMetadata<EmailDelivery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailDelivery> context) {

        EntityWithMetadata<EmailDelivery> entityWithMetadata = context.entityResponse();
        EmailDelivery entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email delivery bounce: {} to {} in state: {}", 
                    entity.getId(), entity.getEmailAddress(), currentState);

        // Set delivery status to BOUNCED
        entity.setDeliveryStatus("BOUNCED");

        // Set error message if not already set
        if (entity.getErrorMessage() == null || entity.getErrorMessage().trim().isEmpty()) {
            entity.setErrorMessage("Email bounced back from recipient server");
        }

        logger.warn("EmailDelivery {} bounced for {}: {}", 
                   entity.getId(), entity.getEmailAddress(), entity.getErrorMessage());

        return entityWithMetadata;
    }
}
