package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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
 * EmailCampaignCreationProcessor - Handles email campaign creation
 * Transition: INITIAL → CREATED
 * 
 * Business Logic:
 * 1. Generate unique campaign ID
 * 2. Set campaign name and type
 * 3. Set scheduled date
 * 4. Initialize counters (totalSubscribers, successfulDeliveries, failedDeliveries) to 0
 * 5. Save campaign entity
 */
@Component
public class EmailCampaignCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailCampaignCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email campaign entity")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailCampaign> entityWithMetadata) {
        EmailCampaign entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main business logic processing method for email campaign creation
     */
    private EntityWithMetadata<EmailCampaign> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email campaign creation: {} in state: {}", entity.getCampaignName(), currentState);

        // Generate unique campaign ID if not already set
        if (entity.getId() == null || entity.getId().trim().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }

        // Set campaign type to "WEEKLY" if not specified
        if (entity.getCampaignType() == null || entity.getCampaignType().trim().isEmpty()) {
            entity.setCampaignType("WEEKLY");
        }

        // Initialize counters to 0
        if (entity.getTotalSubscribers() == null) {
            entity.setTotalSubscribers(0);
        }
        if (entity.getSuccessfulDeliveries() == null) {
            entity.setSuccessfulDeliveries(0);
        }
        if (entity.getFailedDeliveries() == null) {
            entity.setFailedDeliveries(0);
        }

        logger.info("EmailCampaign {} created successfully with ID: {}", entity.getCampaignName(), entity.getId());

        return entityWithMetadata;
    }
}
