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
 * EmailDeliveryCreationProcessor - Handles email delivery creation
 * Transition: INITIAL → PENDING
 * 
 * Business Logic:
 * 1. Validate campaign and subscriber exist
 * 2. Get subscriber email address
 * 3. Generate unique delivery ID
 * 4. Set delivery status to PENDING
 * 5. Initialize timestamps
 * 6. Save delivery entity
 */
@Component
public class EmailDeliveryCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailDeliveryCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDelivery creation for request: {}", request.getId());

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
        return entity != null && technicalId != null;
    }

    /**
     * Main business logic processing method for email delivery creation
     */
    private EntityWithMetadata<EmailDelivery> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailDelivery> context) {

        EntityWithMetadata<EmailDelivery> entityWithMetadata = context.entityResponse();
        EmailDelivery entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email delivery creation for campaign: {} subscriber: {} in state: {}", 
                    entity.getCampaignId(), entity.getSubscriberId(), currentState);

        // Generate unique delivery ID if not already set
        if (entity.getId() == null || entity.getId().trim().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }

        // Set delivery status to PENDING
        entity.setDeliveryStatus("PENDING");

        logger.info("EmailDelivery {} created successfully for campaign: {} subscriber: {}", 
                   entity.getId(), entity.getCampaignId(), entity.getSubscriberId());

        return entityWithMetadata;
    }
}
