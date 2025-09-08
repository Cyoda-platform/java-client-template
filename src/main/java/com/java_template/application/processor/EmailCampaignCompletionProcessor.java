package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * EmailCampaignCompletionProcessor - Handles email campaign completion
 * Transition: EXECUTING → COMPLETED
 * 
 * Business Logic:
 * 1. Validate all email deliveries are processed
 * 2. Calculate final delivery statistics
 * 3. Update success and failure counters
 * 4. Log completion timestamp
 * 5. Generate campaign report
 */
@Component
public class EmailCampaignCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign completion for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "EXECUTING".equals(currentState);
    }

    /**
     * Main business logic processing method for email campaign completion
     */
    private EntityWithMetadata<EmailCampaign> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing email campaign completion: {} in state: {}", entity.getCampaignName(), currentState);

        // Calculate final delivery statistics
        calculateDeliveryStatistics(entity);

        logger.info("EmailCampaign {} completed successfully. Success: {}, Failed: {}", 
                   entity.getCampaignName(), entity.getSuccessfulDeliveries(), entity.getFailedDeliveries());

        return entityWithMetadata;
    }

    /**
     * Calculate final delivery statistics by querying EmailDelivery entities
     */
    private void calculateDeliveryStatistics(EmailCampaign campaign) {
        try {
            ModelSpec deliveryModelSpec = new ModelSpec()
                    .withName(EmailDelivery.ENTITY_NAME)
                    .withVersion(EmailDelivery.ENTITY_VERSION);

            // Create condition to find deliveries for this campaign
            SimpleCondition campaignCondition = new SimpleCondition()
                    .withJsonPath("$.campaignId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(campaign.getId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(campaignCondition));

            List<EntityWithMetadata<EmailDelivery>> deliveries = 
                    entityService.search(deliveryModelSpec, condition, EmailDelivery.class);

            // Count successful and failed deliveries
            int successful = 0;
            int failed = 0;

            List<String> successfulStatuses = Arrays.asList("DELIVERED", "OPENED", "CLICKED");
            List<String> failedStatuses = Arrays.asList("FAILED", "BOUNCED");

            for (EntityWithMetadata<EmailDelivery> deliveryWithMetadata : deliveries) {
                EmailDelivery delivery = deliveryWithMetadata.entity();
                String status = delivery.getDeliveryStatus();
                
                if (successfulStatuses.contains(status)) {
                    successful++;
                } else if (failedStatuses.contains(status)) {
                    failed++;
                }
            }

            // Update campaign statistics
            campaign.setSuccessfulDeliveries(successful);
            campaign.setFailedDeliveries(failed);

            logger.debug("Campaign {} statistics: {} successful, {} failed out of {} total deliveries", 
                        campaign.getCampaignName(), successful, failed, deliveries.size());
            
        } catch (Exception e) {
            logger.error("Error calculating delivery statistics for campaign {}: {}", 
                        campaign.getCampaignName(), e.getMessage());
            // Keep existing values if calculation fails
        }
    }
}
