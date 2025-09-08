package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
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
 * EmailCampaignExecutionProcessor - Handles email campaign execution
 * Transition: SCHEDULED → EXECUTING
 * 
 * Business Logic:
 * 1. Validate campaign is ready for execution
 * 2. Get all active subscribers
 * 3. Update totalSubscribers count
 * 4. Set execution start timestamp
 * 5. Create EmailDelivery entities for each active subscriber
 * 6. Initialize delivery counters
 */
@Component
public class EmailCampaignExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign execution for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "SCHEDULED".equals(currentState);
    }

    /**
     * Main business logic processing method for email campaign execution
     */
    private EntityWithMetadata<EmailCampaign> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email campaign execution: {} in state: {}", entity.getCampaignName(), currentState);

        // Set execution start timestamp
        entity.setExecutedDate(LocalDateTime.now());

        // Get all active subscribers and create email deliveries
        createEmailDeliveries(entity);

        logger.info("EmailCampaign {} execution started with {} deliveries", 
                   entity.getCampaignName(), entity.getTotalSubscribers());

        return entityWithMetadata;
    }

    /**
     * Create EmailDelivery entities for all active subscribers
     */
    private void createEmailDeliveries(EmailCampaign campaign) {
        try {
            ModelSpec subscriberModelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);

            // Create condition to find active subscribers
            SimpleCondition activeCondition = new SimpleCondition()
                    .withJsonPath("$.isActive")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(activeCondition));

            List<EntityWithMetadata<Subscriber>> activeSubscribers = 
                    entityService.search(subscriberModelSpec, condition, Subscriber.class);

            // Update total subscribers count
            campaign.setTotalSubscribers(activeSubscribers.size());

            // Create EmailDelivery entity for each active subscriber
            int deliveriesCreated = 0;
            for (EntityWithMetadata<Subscriber> subscriberWithMetadata : activeSubscribers) {
                Subscriber subscriber = subscriberWithMetadata.entity();
                
                EmailDelivery delivery = new EmailDelivery();
                delivery.setId(UUID.randomUUID().toString());
                delivery.setCampaignId(campaign.getId());
                delivery.setSubscriberId(subscriber.getId());
                delivery.setEmailAddress(subscriber.getEmail());
                delivery.setDeliveryStatus("PENDING");

                try {
                    entityService.create(delivery);
                    deliveriesCreated++;
                } catch (Exception e) {
                    logger.error("Error creating EmailDelivery for subscriber {}: {}", 
                               subscriber.getEmail(), e.getMessage());
                }
            }

            logger.debug("Created {} email deliveries for campaign {}", 
                        deliveriesCreated, campaign.getCampaignName());
            
        } catch (Exception e) {
            logger.error("Error creating email deliveries for campaign {}: {}", 
                        campaign.getCampaignName(), e.getMessage());
        }
    }
}
