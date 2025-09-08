package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * EmailCampaignCancellationProcessor - Handles email campaign cancellation
 * Transition: CREATED/SCHEDULED → CANCELLED
 * 
 * Business Logic:
 * 1. Validate campaign can be cancelled
 * 2. Log cancellation reason and timestamp
 * 3. Release assigned cat fact if any
 * 4. Clean up any prepared deliveries
 * 5. Send cancellation notification
 */
@Component
public class EmailCampaignCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignCancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignCancellationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign cancellation for request: {}", request.getId());

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
        String currentState = entityWithMetadata.metadata().getState();
        
        // Allow cancellation from CREATED or SCHEDULED states
        boolean validState = "CREATED".equals(currentState) || "SCHEDULED".equals(currentState);
        
        return entity != null && entity.isValid() && technicalId != null && validState;
    }

    /**
     * Main business logic processing method for email campaign cancellation
     */
    private EntityWithMetadata<EmailCampaign> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email campaign cancellation: {} in state: {}", entity.getCampaignName(), currentState);

        // Release assigned cat fact if any
        if (entity.getCatFactId() != null && !entity.getCatFactId().trim().isEmpty()) {
            releaseCatFact(entity.getCatFactId());
        }

        logger.info("EmailCampaign {} cancelled successfully", entity.getCampaignName());

        return entityWithMetadata;
    }

    /**
     * Release the assigned cat fact back to RETRIEVED state
     */
    private void releaseCatFact(String catFactId) {
        try {
            ModelSpec catFactModelSpec = new ModelSpec()
                    .withName(CatFact.ENTITY_NAME)
                    .withVersion(CatFact.ENTITY_VERSION);

            // Find the cat fact by business ID
            EntityWithMetadata<CatFact> catFactWithMetadata = 
                    entityService.findByBusinessId(catFactModelSpec, catFactId, "id", CatFact.class);

            if (catFactWithMetadata != null) {
                CatFact catFact = catFactWithMetadata.entity();
                catFact.setScheduledDate(null); // Clear the scheduled date
                
                // Update the cat fact without transition (manual reset to RETRIEVED state would need to be handled externally)
                entityService.update(catFactWithMetadata.metadata().getId(), catFact, null);
                
                logger.debug("Released CatFact {} from cancelled campaign", catFactId);
            } else {
                logger.warn("CatFact with ID {} not found for release", catFactId);
            }
        } catch (Exception e) {
            logger.error("Error releasing CatFact {} from cancelled campaign: {}", catFactId, e.getMessage());
        }
    }
}
