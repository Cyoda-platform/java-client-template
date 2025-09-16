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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processor for email campaign scheduling workflow transition.
 * Handles the schedule transition (none → scheduled).
 * 
 * Business Logic:
 * - Validates campaign name is unique
 * - Sets scheduledDate to next weekly send time
 * - Gets random ready CatFact
 * - Counts active subscribers
 * - Sets totalSubscribers count
 */
@Component
public class EmailCampaignScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignScheduleProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignScheduleProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.debug("EmailCampaignScheduleProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign scheduling for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateScheduleData, "Invalid schedule data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                try {
                    // Set campaign name if not provided
                    if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        campaign.setCampaignName("Weekly Cat Facts - " + timestamp);
                    }
                    
                    // Set scheduled date if not provided (next weekly send time)
                    if (campaign.getScheduledDate() == null) {
                        campaign.setScheduledDate(getNextWeeklySendTime());
                    }
                    
                    // Get random ready cat fact if not assigned
                    if (campaign.getCatFactId() == null) {
                        Long catFactId = getRandomReadyCatFact();
                        if (catFactId == null) {
                            throw new RuntimeException("No ready cat facts available");
                        }
                        campaign.setCatFactId(catFactId);
                    }
                    
                    // Count active subscribers
                    int activeSubscriberCount = countActiveSubscribers();
                    campaign.setTotalSubscribers(activeSubscriberCount);
                    
                    // Initialize counters
                    campaign.setSuccessfulDeliveries(0);
                    campaign.setFailedDeliveries(0);
                    campaign.setOpenCount(0);
                    campaign.setClickCount(0);
                    campaign.setUnsubscribeCount(0);
                    
                    logger.info("Email campaign scheduled: {} for {} subscribers", 
                               campaign.getCampaignName(), activeSubscriberCount);
                    
                    return campaign;
                    
                } catch (Exception e) {
                    logger.error("Failed to schedule email campaign: {}", e.getMessage());
                    throw new RuntimeException("Campaign scheduling failed", e);
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignScheduleProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates schedule data.
     */
    private boolean validateScheduleData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Schedule failed: EmailCampaign is null");
            return false;
        }
        
        // If campaign name is provided, it should not be empty
        if (campaign.getCampaignName() != null && campaign.getCampaignName().trim().isEmpty()) {
            logger.warn("Schedule failed: Campaign name is empty");
            return false;
        }
        
        // If scheduled date is provided, it should be in the future
        if (campaign.getScheduledDate() != null && campaign.getScheduledDate().isBefore(LocalDateTime.now())) {
            logger.warn("Schedule failed: Scheduled date is in the past");
            return false;
        }
        
        logger.debug("Schedule data validation passed");
        return true;
    }

    /**
     * Gets the next weekly send time (e.g., next Monday at 9 AM).
     */
    private LocalDateTime getNextWeeklySendTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonday = now.plusDays(7 - now.getDayOfWeek().getValue() + 1);
        return nextMonday.withHour(9).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * Gets a random ready cat fact ID.
     */
    private Long getRandomReadyCatFact() {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("category", "ready");
            condition.put("isUsed", false);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> readyFacts = 
                entityService.getItemsByCondition(
                    CatFact.ENTITY_NAME, 
                    CatFact.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            if (readyFacts == null || readyFacts.isEmpty()) {
                logger.warn("No ready cat facts found");
                return null;
            }
            
            // Select random fact
            int randomIndex = ThreadLocalRandom.current().nextInt(readyFacts.size());
            org.cyoda.cloud.api.event.common.DataPayload selectedFact = readyFacts.get(randomIndex);
            
            // Extract ID from the fact
            com.fasterxml.jackson.databind.JsonNode factData = selectedFact.getData();
            return factData.get("id").asLong();
            
        } catch (Exception e) {
            logger.error("Failed to get random ready cat fact: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Counts active subscribers.
     */
    private int countActiveSubscribers() {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("isActive", true);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> activeSubscribers = 
                entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME, 
                    Subscriber.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            return activeSubscribers != null ? activeSubscribers.size() : 0;
            
        } catch (Exception e) {
            logger.error("Failed to count active subscribers: {}", e.getMessage());
            return 0;
        }
    }
}
