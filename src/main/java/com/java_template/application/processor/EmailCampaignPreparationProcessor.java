package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor for email campaign preparation workflow transition.
 * Handles the prepare transition (scheduled → preparing).
 * 
 * Business Logic:
 * - Gets CatFact by catFactId
 * - Gets list of active subscribers
 * - Prepares email template with cat fact content
 * - Generates personalized emails for each subscriber
 * - Validates email content and recipients
 * - Updates totalSubscribers with current count
 */
@Component
public class EmailCampaignPreparationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignPreparationProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignPreparationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.debug("EmailCampaignPreparationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign preparation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validatePreparationData, "Invalid preparation data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                try {
                    // Get the cat fact for this campaign
                    CatFact catFact = getCatFactById(campaign.getCatFactId());
                    if (catFact == null) {
                        throw new RuntimeException("Cat fact not found: " + campaign.getCatFactId());
                    }
                    
                    // Get list of active subscribers
                    List<Subscriber> activeSubscribers = getActiveSubscribers();
                    if (activeSubscribers.isEmpty()) {
                        throw new RuntimeException("No active subscribers found");
                    }
                    
                    // Update total subscribers count with current count
                    campaign.setTotalSubscribers(activeSubscribers.size());
                    
                    // Prepare email template (simplified - in real implementation would use template engine)
                    String emailTemplate = prepareEmailTemplate(catFact);
                    
                    // Validate email content
                    if (!validateEmailContent(emailTemplate)) {
                        throw new RuntimeException("Email content validation failed");
                    }
                    
                    // Generate personalized emails (simplified - would store in cache/database)
                    int personalizedEmailCount = generatePersonalizedEmails(activeSubscribers, emailTemplate);
                    
                    logger.info("Email campaign prepared: {} with {} personalized emails", 
                               campaign.getCampaignName(), personalizedEmailCount);
                    
                    return campaign;
                    
                } catch (Exception e) {
                    logger.error("Failed to prepare email campaign: {}", e.getMessage());
                    throw new RuntimeException("Campaign preparation failed", e);
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignPreparationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates preparation data.
     */
    private boolean validatePreparationData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Preparation failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign name must be set
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Preparation failed: Campaign name is required");
            return false;
        }
        
        // Cat fact ID must be set
        if (campaign.getCatFactId() == null) {
            logger.warn("Preparation failed: Cat fact ID is required");
            return false;
        }
        
        // Scheduled date must be set
        if (campaign.getScheduledDate() == null) {
            logger.warn("Preparation failed: Scheduled date is required");
            return false;
        }
        
        logger.debug("Preparation data validation passed");
        return true;
    }

    /**
     * Gets a cat fact by ID.
     */
    private CatFact getCatFactById(Long catFactId) {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("id", catFactId);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> facts = 
                entityService.getItemsByCondition(
                    CatFact.ENTITY_NAME, 
                    CatFact.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            if (facts == null || facts.isEmpty()) {
                return null;
            }
            
            // Convert to CatFact entity (simplified)
            com.fasterxml.jackson.databind.JsonNode factData = facts.get(0).getData();
            CatFact catFact = new CatFact();
            catFact.setId(factData.get("id").asLong());
            catFact.setFactText(factData.get("factText").asText());
            catFact.setSource(factData.get("source").asText());
            
            return catFact;
            
        } catch (Exception e) {
            logger.error("Failed to get cat fact by ID {}: {}", catFactId, e.getMessage());
            return null;
        }
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
                    subscriber.setLastName(subData.has("lastName") ? subData.get("lastName").asText() : null);
                    return subscriber;
                })
                .toList();
            
        } catch (Exception e) {
            logger.error("Failed to get active subscribers: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Prepares email template with cat fact content.
     */
    private String prepareEmailTemplate(CatFact catFact) {
        return String.format("""
            Subject: Your Weekly Cat Fact!
            
            Hello {{firstName}},
            
            Here's your weekly cat fact:
            
            %s
            
            Did you know that? We hope you enjoyed this week's cat fact!
            
            Best regards,
            The Cat Facts Team
            
            To unsubscribe, click here: {{unsubscribeLink}}
            """, catFact.getFactText());
    }

    /**
     * Validates email content.
     */
    private boolean validateEmailContent(String emailTemplate) {
        return emailTemplate != null && 
               !emailTemplate.trim().isEmpty() && 
               emailTemplate.contains("{{firstName}}") && 
               emailTemplate.contains("{{unsubscribeLink}}");
    }

    /**
     * Generates personalized emails for each subscriber.
     */
    private int generatePersonalizedEmails(List<Subscriber> subscribers, String template) {
        // In a real implementation, this would generate and store personalized emails
        // For now, we just simulate the process
        logger.debug("Generated {} personalized emails", subscribers.size());
        return subscribers.size();
    }
}
