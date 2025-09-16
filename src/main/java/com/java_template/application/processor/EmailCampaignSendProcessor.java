package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processor for email campaign sending workflow transition.
 * Handles the start_sending transition (preparing → sending).
 * 
 * Business Logic:
 * - Sets sendStartTime to current timestamp
 * - Initializes delivery counters
 * - Sends personalized emails to each active subscriber
 * - Increments success/failure counters
 * - Handles email bounces by triggering subscriber suspension
 * - Transitions CatFact to used state
 */
@Component
public class EmailCampaignSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignSendProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignSendProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.debug("EmailCampaignSendProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign sending for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateSendData, "Invalid send data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                try {
                    // Set send start time
                    campaign.setSentDate(LocalDateTime.now());
                    
                    // Initialize delivery counters
                    campaign.setSuccessfulDeliveries(0);
                    campaign.setFailedDeliveries(0);
                    
                    // Get active subscribers
                    List<Subscriber> activeSubscribers = getActiveSubscribers();
                    
                    // Send emails to each subscriber
                    for (Subscriber subscriber : activeSubscribers) {
                        try {
                            boolean emailSent = sendEmailToSubscriber(subscriber, campaign);
                            
                            if (emailSent) {
                                campaign.setSuccessfulDeliveries(campaign.getSuccessfulDeliveries() + 1);
                                logger.debug("Email sent successfully to: {}", subscriber.getEmail());
                            } else {
                                campaign.setFailedDeliveries(campaign.getFailedDeliveries() + 1);
                                logger.warn("Email failed to send to: {}", subscriber.getEmail());
                                
                                // Handle email bounce - trigger subscriber suspension
                                handleEmailBounce(subscriber);
                            }
                            
                        } catch (Exception e) {
                            campaign.setFailedDeliveries(campaign.getFailedDeliveries() + 1);
                            logger.error("Email sending failed for {}: {}", subscriber.getEmail(), e.getMessage());
                        }
                    }
                    
                    // Mark cat fact as used
                    markCatFactAsUsed(campaign.getCatFactId());
                    
                    logger.info("Email campaign sending completed: {} (Success: {}, Failed: {})", 
                               campaign.getCampaignName(), 
                               campaign.getSuccessfulDeliveries(), 
                               campaign.getFailedDeliveries());
                    
                    return campaign;
                    
                } catch (Exception e) {
                    logger.error("Failed to send email campaign: {}", e.getMessage());
                    throw new RuntimeException("Campaign sending failed", e);
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignSendProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates send data.
     */
    private boolean validateSendData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Send failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign name must be set
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Send failed: Campaign name is required");
            return false;
        }
        
        // Cat fact ID must be set
        if (campaign.getCatFactId() == null) {
            logger.warn("Send failed: Cat fact ID is required");
            return false;
        }
        
        // Total subscribers should be greater than 0
        if (campaign.getTotalSubscribers() == null || campaign.getTotalSubscribers() <= 0) {
            logger.warn("Send failed: No subscribers to send to");
            return false;
        }
        
        logger.debug("Send data validation passed");
        return true;
    }

    /**
     * Gets list of active subscribers.
     */
    private List<Subscriber> getActiveSubscribers() {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("isActive", true);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> subscriberData = 
                entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME, 
                    Subscriber.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            // Convert to Subscriber entities (simplified)
            return subscriberData.stream()
                .map(data -> {
                    com.fasterxml.jackson.databind.JsonNode subData = data.getData();
                    Subscriber subscriber = new Subscriber();
                    subscriber.setId(subData.get("id").asLong());
                    subscriber.setEmail(subData.get("email").asText());
                    subscriber.setFirstName(subData.has("firstName") ? subData.get("firstName").asText() : null);
                    return subscriber;
                })
                .toList();
            
        } catch (Exception e) {
            logger.error("Failed to get active subscribers: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Sends email to a specific subscriber.
     * Simulates email sending with random success/failure.
     */
    private boolean sendEmailToSubscriber(Subscriber subscriber, EmailCampaign campaign) {
        // Simulate email sending with 95% success rate
        boolean success = ThreadLocalRandom.current().nextDouble() < 0.95;
        
        if (success) {
            logger.debug("Simulated email sent to: {}", subscriber.getEmail());
        } else {
            logger.debug("Simulated email bounce for: {}", subscriber.getEmail());
        }
        
        return success;
    }

    /**
     * Handles email bounce by triggering subscriber suspension.
     */
    private void handleEmailBounce(Subscriber subscriber) {
        try {
            // Find the subscriber entity by email to get the UUID
            Map<String, Object> condition = new HashMap<>();
            condition.put("email", subscriber.getEmail());
            
            List<org.cyoda.cloud.api.event.common.DataPayload> subscriberData = 
                entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME, 
                    Subscriber.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            if (!subscriberData.isEmpty()) {
                // Extract entity ID and trigger suspension
                com.fasterxml.jackson.databind.JsonNode subData = subscriberData.get(0).getData();
                String entityIdStr = subData.get("id").asText();
                UUID entityId = UUID.fromString(entityIdStr);
                
                // Apply suspension transition asynchronously
                entityService.applyTransition(entityId, "suspend")
                    .thenAccept(transitions -> 
                        logger.info("Triggered suspension for bounced email: {}", subscriber.getEmail()))
                    .exceptionally(ex -> {
                        logger.error("Failed to suspend subscriber {}: {}", subscriber.getEmail(), ex.getMessage());
                        return null;
                    });
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle email bounce for {}: {}", subscriber.getEmail(), e.getMessage());
        }
    }

    /**
     * Marks the cat fact as used.
     */
    private void markCatFactAsUsed(Long catFactId) {
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
                // Extract entity ID and trigger use transition
                com.fasterxml.jackson.databind.JsonNode factJson = factData.get(0).getData();
                String entityIdStr = factJson.get("id").asText();
                UUID entityId = UUID.fromString(entityIdStr);
                
                // Apply use transition asynchronously
                entityService.applyTransition(entityId, "use")
                    .thenAccept(transitions -> 
                        logger.info("Marked cat fact as used: {}", catFactId))
                    .exceptionally(ex -> {
                        logger.error("Failed to mark cat fact as used {}: {}", catFactId, ex.getMessage());
                        return null;
                    });
            }
            
        } catch (Exception e) {
            logger.error("Failed to mark cat fact as used {}: {}", catFactId, e.getMessage());
        }
    }
}
