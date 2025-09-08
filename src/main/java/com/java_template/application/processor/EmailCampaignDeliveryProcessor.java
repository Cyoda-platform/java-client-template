package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailCampaignDeliveryProcessor - Executes email campaign delivery
 * 
 * Input: EmailCampaign entity in SENT state
 * Purpose: Complete successful email campaign delivery
 * Output: EmailCampaign entity in DELIVERED state
 */
@Component
public class EmailCampaignDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignDeliveryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign delivery for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email campaign for delivery")
                .map(this::processEmailCampaignDelivery)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for delivery
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailCampaign> entityWithMetadata) {
        EmailCampaign campaign = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return campaign != null && 
               campaign.isValid() && 
               "sent".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for email campaign delivery
     */
    private EntityWithMetadata<EmailCampaign> processEmailCampaignDelivery(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign campaign = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing delivery for campaign: {} in state: {}", campaign.getCampaignId(), currentState);

        // Verify campaign is in SENT state
        if (!"sent".equalsIgnoreCase(currentState)) {
            logger.warn("EmailCampaign {} is not in SENT state, current state: {}", 
                       campaign.getCampaignId(), currentState);
            return entityWithMetadata;
        }

        // Set actual sent date if not already set
        if (campaign.getActualSentDate() == null) {
            campaign.setActualSentDate(LocalDateTime.now());
        }

        // Simulate email delivery to all active subscribers
        deliverEmailsToSubscribers(campaign);

        // Finalize delivery statistics
        finalizeDeliveryStatistics(campaign);

        logger.info("EmailCampaign {} delivery completed successfully. Success: {}, Failed: {}", 
                   campaign.getCampaignId(), campaign.getSuccessfulDeliveries(), campaign.getFailedDeliveries());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Simulates delivering emails to all active subscribers
     */
    private void deliverEmailsToSubscribers(EmailCampaign campaign) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            
            // Create condition to find active subscribers
            SimpleCondition activeCondition = new SimpleCondition()
                    .withJsonPath("$.isActive")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(activeCondition));

            List<EntityWithMetadata<Subscriber>> activeSubscribers = entityService.search(modelSpec, condition, Subscriber.class);

            int successful = 0;
            int failed = 0;

            for (EntityWithMetadata<Subscriber> subscriberWithMetadata : activeSubscribers) {
                Subscriber subscriber = subscriberWithMetadata.entity();
                
                // Simulate email delivery (90% success rate)
                boolean deliverySuccess = Math.random() > 0.1;
                
                if (deliverySuccess) {
                    successful++;
                    // Update subscriber's email tracking
                    subscriber.setLastEmailSent(LocalDateTime.now());
                    subscriber.setTotalEmailsReceived(subscriber.getTotalEmailsReceived() + 1);
                    
                    // Update subscriber without transition (loop back to same state)
                    entityService.update(subscriberWithMetadata.metadata().getId(), subscriber, null);
                } else {
                    failed++;
                }
            }

            campaign.setSuccessfulDeliveries(successful);
            campaign.setFailedDeliveries(failed);
            
        } catch (Exception e) {
            logger.error("Error delivering emails for campaign: {}", campaign.getCampaignId(), e);
            campaign.setFailedDeliveries(campaign.getTotalSubscribers());
            campaign.setSuccessfulDeliveries(0);
        }
    }

    /**
     * Finalizes delivery statistics for the campaign
     */
    private void finalizeDeliveryStatistics(EmailCampaign campaign) {
        // Calculate engagement metrics (simulated)
        int totalDelivered = campaign.getSuccessfulDeliveries();
        
        if (totalDelivered > 0) {
            // Simulate opens (60% open rate)
            campaign.setOpens((int) (totalDelivered * 0.6));
            
            // Simulate clicks (15% click rate of opens)
            campaign.setClicks((int) (campaign.getOpens() * 0.15));
            
            // Simulate bounces (2% of failed deliveries)
            campaign.setBounces((int) (campaign.getFailedDeliveries() * 0.02));
            
            // Simulate unsubscribes (1% of delivered)
            campaign.setUnsubscribes((int) (totalDelivered * 0.01));
        }
    }
}
