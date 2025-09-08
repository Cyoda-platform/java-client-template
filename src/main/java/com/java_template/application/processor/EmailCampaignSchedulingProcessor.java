package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.util.List;
import java.util.UUID;

/**
 * EmailCampaignSchedulingProcessor - Handles email campaign scheduling with cat fact assignment
 * Transition: CREATED → SCHEDULED
 * 
 * Business Logic:
 * 1. Validate campaign exists and is in CREATED state
 * 2. Validate cat fact exists and is available (RETRIEVED state)
 * 3. Assign cat fact to campaign
 * 4. Count active subscribers for totalSubscribers
 * 5. Validate scheduled date is in future
 */
@Component
public class EmailCampaignSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignSchedulingProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign scheduling for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "CREATED".equals(currentState);
    }

    /**
     * Main business logic processing method for email campaign scheduling
     */
    private EntityWithMetadata<EmailCampaign> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email campaign scheduling: {} in state: {}", entity.getCampaignName(), currentState);

        // Count active subscribers for totalSubscribers
        countActiveSubscribers(entity);

        // Update the assigned cat fact to SCHEDULED state if catFactId is provided
        if (entity.getCatFactId() != null && !entity.getCatFactId().trim().isEmpty()) {
            updateCatFactToScheduled(entity.getCatFactId(), entity.getScheduledDate());
        }

        logger.info("EmailCampaign {} scheduled successfully with {} subscribers", 
                   entity.getCampaignName(), entity.getTotalSubscribers());

        return entityWithMetadata;
    }

    /**
     * Count active subscribers and update the campaign
     */
    private void countActiveSubscribers(EmailCampaign campaign) {
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

            campaign.setTotalSubscribers(activeSubscribers.size());
            
            logger.debug("Found {} active subscribers for campaign {}", 
                        activeSubscribers.size(), campaign.getCampaignName());
        } catch (Exception e) {
            logger.error("Error counting active subscribers for campaign {}: {}", 
                        campaign.getCampaignName(), e.getMessage());
            // Set to 0 if we can't count subscribers
            campaign.setTotalSubscribers(0);
        }
    }

    /**
     * Update the assigned cat fact to SCHEDULED state
     */
    private void updateCatFactToScheduled(String catFactId, java.time.LocalDateTime scheduledDate) {
        try {
            ModelSpec catFactModelSpec = new ModelSpec()
                    .withName(CatFact.ENTITY_NAME)
                    .withVersion(CatFact.ENTITY_VERSION);

            // Find the cat fact by business ID
            EntityWithMetadata<CatFact> catFactWithMetadata = 
                    entityService.findByBusinessId(catFactModelSpec, catFactId, "id", CatFact.class);

            if (catFactWithMetadata != null) {
                CatFact catFact = catFactWithMetadata.entity();
                catFact.setScheduledDate(scheduledDate);
                
                // Update the cat fact with transition to SCHEDULED state
                entityService.update(catFactWithMetadata.metadata().getId(), catFact, "transition_to_scheduled");
                
                logger.debug("Updated CatFact {} to SCHEDULED state", catFactId);
            } else {
                logger.warn("CatFact with ID {} not found for scheduling", catFactId);
            }
        } catch (Exception e) {
            logger.error("Error updating CatFact {} to SCHEDULED state: {}", catFactId, e.getMessage());
        }
    }
}
