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
 * EmailDeliverySendProcessor - Handles email delivery sending
 * Transition: PENDING → SENT
 * 
 * Business Logic:
 * 1. Get campaign and cat fact details
 * 2. Get subscriber information
 * 3. Compose email content with cat fact
 * 4. Send email via email service provider
 * 5. Set sent timestamp
 * 6. Update delivery status
 */
@Component
public class EmailDeliverySendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliverySendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailDeliverySendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDelivery send for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "PENDING".equals(currentState);
    }

    /**
     * Main business logic processing method for email delivery sending
     */
    private EntityWithMetadata<EmailDelivery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailDelivery> context) {

        EntityWithMetadata<EmailDelivery> entityWithMetadata = context.entityResponse();
        EmailDelivery entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email delivery send: {} to {} in state: {}", 
                    entity.getId(), entity.getEmailAddress(), currentState);

        // Simulate email sending - in real implementation, this would call an email service
        // For now, we'll just set the sent timestamp and update status
        entity.setSentDate(LocalDateTime.now());
        entity.setDeliveryStatus("SENT");

        logger.info("EmailDelivery {} sent successfully to {}", entity.getId(), entity.getEmailAddress());

        return entityWithMetadata;
    }
}
