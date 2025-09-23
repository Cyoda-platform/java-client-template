package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.catfact.version_1.CatFact;
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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EmailCampaignProcessor - Handles email campaign creation and sending
 * 
 * This processor manages email campaigns, retrieves active subscribers,
 * fetches cat facts, and simulates email sending with tracking.
 */
@Component
public class EmailCampaignProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EmailCampaign entity wrapper")
                .map(this::processCampaignLogic)
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
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for email campaign processing
     */
    private EntityWithMetadata<EmailCampaign> processCampaignLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing campaign: {} in state: {}", entity.getCampaignId(), currentState);

        // Process based on current state and transition
        if ("draft".equals(currentState) || "initial".equals(currentState)) {
            prepareCampaign(entity);
        } else if ("sending".equals(currentState)) {
            executeCampaign(entity);
        }

        // Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        logger.info("EmailCampaign {} processed successfully", entity.getCampaignId());

        return entityWithMetadata;
    }

    /**
     * Prepares the campaign by setting up basic information
     */
    private void prepareCampaign(EmailCampaign entity) {
        logger.debug("Preparing campaign: {}", entity.getCampaignId());

        // Set default campaign name if not provided
        if (entity.getCampaignName() == null || entity.getCampaignName().trim().isEmpty()) {
            entity.setCampaignName("Weekly Cat Fact - " + LocalDateTime.now().toLocalDate());
        }

        // Set default email subject
        if (entity.getEmailSubject() == null || entity.getEmailSubject().trim().isEmpty()) {
            entity.setEmailSubject("Your Weekly Cat Fact!");
        }

        // Set default email template
        if (entity.getEmailTemplate() == null || entity.getEmailTemplate().trim().isEmpty()) {
            entity.setEmailTemplate("weekly_cat_fact_template");
        }

        // Set status
        entity.setStatus(EmailCampaign.CampaignStatus.DRAFT);

        // Initialize counters
        if (entity.getRecipientCount() == null) entity.setRecipientCount(0);
        if (entity.getSuccessCount() == null) entity.setSuccessCount(0);
        if (entity.getFailureCount() == null) entity.setFailureCount(0);

        logger.debug("Campaign {} prepared successfully", entity.getCampaignId());
    }

    /**
     * Executes the campaign by sending emails to subscribers
     */
    private void executeCampaign(EmailCampaign entity) {
        logger.debug("Executing campaign: {}", entity.getCampaignId());

        try {
            // Get active subscribers
            List<EntityWithMetadata<Subscriber>> activeSubscribers = getActiveSubscribers();
            entity.setRecipientCount(activeSubscribers.size());

            // Get cat fact content
            String catFactContent = getCatFactContent(entity.getCatFactId());

            // Simulate email sending
            List<EmailCampaign.EmailDeliveryResult> deliveryResults = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (EntityWithMetadata<Subscriber> subscriberWithMetadata : activeSubscribers) {
                Subscriber subscriber = subscriberWithMetadata.entity();
                EmailCampaign.EmailDeliveryResult result = sendEmailToSubscriber(subscriber, catFactContent);
                deliveryResults.add(result);

                if ("SENT".equals(result.getDeliveryStatus()) || "DELIVERED".equals(result.getDeliveryStatus())) {
                    successCount++;
                    // Update subscriber's last email sent timestamp
                    updateSubscriberEmailTracking(subscriberWithMetadata, entity);
                } else {
                    failureCount++;
                }
            }

            // Update campaign results
            entity.setSuccessCount(successCount);
            entity.setFailureCount(failureCount);
            entity.setDeliveryResults(deliveryResults);
            entity.setSentDate(LocalDateTime.now());
            entity.setStatus(EmailCampaign.CampaignStatus.SENDING);

            // Calculate and set metrics
            updateCampaignMetrics(entity);

            logger.info("Campaign {} executed: {} sent, {} failed", 
                       entity.getCampaignId(), successCount, failureCount);

        } catch (Exception e) {
            logger.error("Error executing campaign: {}", entity.getCampaignId(), e);
            entity.setStatus(EmailCampaign.CampaignStatus.FAILED);
        }
    }

    /**
     * Retrieves active subscribers from the database
     */
    private List<EntityWithMetadata<Subscriber>> getActiveSubscribers() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);

            SimpleCondition activeCondition = new SimpleCondition()
                    .withJsonPath("$.isActive")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) activeCondition));

            return entityService.search(modelSpec, condition, Subscriber.class);
        } catch (Exception e) {
            logger.error("Error retrieving active subscribers", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets cat fact content by ID
     */
    private String getCatFactContent(String catFactId) {
        try {
            if (catFactId != null && !catFactId.trim().isEmpty()) {
                ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
                EntityWithMetadata<CatFact> catFactWithMetadata = entityService.findByBusinessId(
                        modelSpec, catFactId, "factId", CatFact.class);
                
                if (catFactWithMetadata != null) {
                    return catFactWithMetadata.entity().getContent();
                }
            }
            
            // Fallback to default content
            return "Did you know? Cats are amazing creatures with many fascinating traits!";
        } catch (Exception e) {
            logger.error("Error retrieving cat fact content for ID: {}", catFactId, e);
            return "Did you know? Cats are amazing creatures with many fascinating traits!";
        }
    }

    /**
     * Simulates sending email to a subscriber
     */
    private EmailCampaign.EmailDeliveryResult sendEmailToSubscriber(Subscriber subscriber, String catFactContent) {
        EmailCampaign.EmailDeliveryResult result = new EmailCampaign.EmailDeliveryResult();
        result.setSubscriberId(subscriber.getSubscriberId());
        result.setEmail(subscriber.getEmail());
        result.setDeliveryTime(LocalDateTime.now());

        // Simulate email delivery (90% success rate)
        boolean success = Math.random() < 0.9;
        
        if (success) {
            result.setDeliveryStatus("SENT");
            result.setOpened(Math.random() < 0.3); // 30% open rate
            result.setClicked(result.getOpened() && Math.random() < 0.1); // 10% click rate if opened
        } else {
            result.setDeliveryStatus("FAILED");
            result.setErrorMessage("Simulated delivery failure");
            result.setOpened(false);
            result.setClicked(false);
        }

        return result;
    }

    /**
     * Updates subscriber's email tracking information
     */
    private void updateSubscriberEmailTracking(EntityWithMetadata<Subscriber> subscriberWithMetadata, EmailCampaign campaign) {
        try {
            Subscriber subscriber = subscriberWithMetadata.entity();
            subscriber.setLastEmailSent(campaign.getSentDate());
            
            if (subscriber.getTotalEmailsReceived() == null) {
                subscriber.setTotalEmailsReceived(1);
            } else {
                subscriber.setTotalEmailsReceived(subscriber.getTotalEmailsReceived() + 1);
            }

            entityService.update(subscriberWithMetadata.metadata().getId(), subscriber, "update_subscriber");
        } catch (Exception e) {
            logger.error("Error updating subscriber email tracking for: {}", 
                        subscriberWithMetadata.entity().getSubscriberId(), e);
        }
    }

    /**
     * Calculates and updates campaign metrics
     */
    private void updateCampaignMetrics(EmailCampaign entity) {
        if (entity.getDeliveryResults() != null && !entity.getDeliveryResults().isEmpty()) {
            EmailCampaign.CampaignMetrics metrics = new EmailCampaign.CampaignMetrics();
            
            int totalSent = entity.getSuccessCount();
            int totalOpens = 0;
            int totalClicks = 0;
            int totalBounces = entity.getFailureCount();

            for (EmailCampaign.EmailDeliveryResult result : entity.getDeliveryResults()) {
                if (Boolean.TRUE.equals(result.getOpened())) totalOpens++;
                if (Boolean.TRUE.equals(result.getClicked())) totalClicks++;
            }

            metrics.setTotalOpens(totalOpens);
            metrics.setTotalClicks(totalClicks);
            metrics.setTotalBounces(totalBounces);

            if (totalSent > 0) {
                metrics.setDeliveryRate((double) totalSent / entity.getRecipientCount());
                metrics.setOpenRate((double) totalOpens / totalSent);
                metrics.setBounceRate((double) totalBounces / entity.getRecipientCount());
            }

            if (totalOpens > 0) {
                metrics.setClickRate((double) totalClicks / totalOpens);
            }

            metrics.setLastUpdated(LocalDateTime.now());
            entity.setMetrics(metrics);
        }
    }
}
