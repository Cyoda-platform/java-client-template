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
import java.util.UUID;

/**
 * EmailCampaignScheduleProcessor - Creates and schedules new email campaign
 * 
 * Input: Campaign scheduling data (catFactId, scheduledDate)
 * Purpose: Create and schedule new email campaign
 * Output: EmailCampaign entity in PENDING state
 */
@Component
public class EmailCampaignScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignScheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignScheduleProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign schedule for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email campaign for scheduling")
                .map(this::processEmailCampaignSchedule)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for scheduling
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailCampaign> entityWithMetadata) {
        EmailCampaign campaign = entityWithMetadata.entity();
        
        return campaign != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for email campaign scheduling
     */
    private EntityWithMetadata<EmailCampaign> processEmailCampaignSchedule(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign campaign = entityWithMetadata.entity();

        logger.debug("Scheduling email campaign");

        // Generate unique campaignId if not set
        if (campaign.getCampaignId() == null || campaign.getCampaignId().trim().isEmpty()) {
            campaign.setCampaignId("campaign_" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Set default campaign name if not provided
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            campaign.setCampaignName("Weekly Cat Facts - " + LocalDateTime.now().toLocalDate());
        }

        // Set default scheduled date if not provided
        if (campaign.getScheduledDate() == null) {
            campaign.setScheduledDate(LocalDateTime.now().plusMinutes(5)); // Schedule 5 minutes from now
        }

        // Count active subscribers for totalSubscribers
        int activeSubscriberCount = countActiveSubscribers();
        campaign.setTotalSubscribers(activeSubscriberCount);

        // Initialize all delivery counters to 0
        campaign.setSuccessfulDeliveries(0);
        campaign.setFailedDeliveries(0);
        campaign.setBounces(0);
        campaign.setUnsubscribes(0);
        campaign.setOpens(0);
        campaign.setClicks(0);

        // Set default email subject and template if not provided
        if (campaign.getEmailSubject() == null || campaign.getEmailSubject().trim().isEmpty()) {
            campaign.setEmailSubject("Your Weekly Cat Fact is Here!");
        }
        
        if (campaign.getEmailTemplate() == null || campaign.getEmailTemplate().trim().isEmpty()) {
            campaign.setEmailTemplate("weekly_template");
        }

        logger.info("EmailCampaign {} scheduled successfully with {} subscribers", 
                   campaign.getCampaignId(), activeSubscriberCount);

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Counts active subscribers for the campaign
     */
    private int countActiveSubscribers() {
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
            return activeSubscribers.size();
        } catch (Exception e) {
            logger.error("Error counting active subscribers", e);
            return 0;
        }
    }
}
