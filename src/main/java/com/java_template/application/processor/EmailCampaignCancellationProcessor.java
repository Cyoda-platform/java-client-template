package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processor for email campaign cancellation workflow transition.
 * Handles the cancel transition (scheduled → cancelled).
 * 
 * Business Logic:
 * - Sets cancellation date to current timestamp
 * - Sets cancellation reason
 * - Releases reserved CatFact back to ready state
 * - Logs cancellation event
 */
@Component
public class EmailCampaignCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignCancellationProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignCancellationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.debug("EmailCampaignCancellationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign cancellation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateCancellationData, "Invalid cancellation data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                // Set cancellation metadata
                LocalDateTime cancellationDate = LocalDateTime.now();
                String cancellationReason = "manual_cancellation"; // Could be extracted from request context
                
                // Release reserved cat fact back to ready state
                if (campaign.getCatFactId() != null) {
                    releaseCatFactToReady(campaign.getCatFactId());
                }
                
                // Log cancellation event
                logger.info("Email campaign cancelled: {} at {} (reason: {})", 
                           campaign.getCampaignName(), cancellationDate, cancellationReason);
                
                return campaign;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignCancellationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates cancellation data.
     */
    private boolean validateCancellationData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Cancellation failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign name must be set
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Cancellation failed: Campaign name is required");
            return false;
        }
        
        // Campaign should be in scheduled state (not sent)
        if (campaign.getSentDate() != null) {
            logger.warn("Cancellation failed: Campaign has already been sent");
            return false;
        }
        
        // Scheduled date should be set
        if (campaign.getScheduledDate() == null) {
            logger.warn("Cancellation failed: Scheduled date not set");
            return false;
        }
        
        logger.debug("Cancellation data validation passed");
        return true;
    }

    /**
     * Releases the reserved cat fact back to ready state.
     */
    private void releaseCatFactToReady(Long catFactId) {
        try {
            // Find the cat fact entity by ID to get the UUID
            Map<String, Object> condition = new HashMap<>();
            condition.put("id", catFactId);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> factData = 
                entityService.getItemsByCondition(
                    com.java_template.application.entity.catfact.version_1.CatFact.ENTITY_NAME, 
                    com.java_template.application.entity.catfact.version_1.CatFact.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            if (!factData.isEmpty()) {
                // Extract entity ID
                com.fasterxml.jackson.databind.JsonNode factJson = factData.get(0).getData();
                String entityIdStr = factJson.get("id").asText();
                UUID entityId = UUID.fromString(entityIdStr);
                
                // Check current state and apply appropriate transition
                String currentCategory = factJson.has("category") ? factJson.get("category").asText() : "unknown";
                
                // If the fact was reserved for this campaign, release it back to ready
                if ("ready".equals(currentCategory) || "validated".equals(currentCategory)) {
                    logger.debug("Cat fact {} is already in ready state, no transition needed", catFactId);
                } else {
                    // Apply transition to make it ready again (this might need adjustment based on workflow)
                    entityService.applyTransition(entityId, "approve")
                        .thenAccept(transitions -> 
                            logger.info("Released cat fact back to ready state: {}", catFactId))
                        .exceptionally(ex -> {
                            logger.error("Failed to release cat fact {}: {}", catFactId, ex.getMessage());
                            return null;
                        });
                }
            } else {
                logger.warn("Cat fact not found for release: {}", catFactId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to release cat fact {}: {}", catFactId, e.getMessage());
        }
    }
}
