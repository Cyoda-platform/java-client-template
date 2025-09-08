package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * EmailCampaignFailProcessor - Handles failed email campaign
 * 
 * Input: EmailCampaign entity in SENT state
 * Purpose: Handle failed email campaign
 * Output: EmailCampaign entity in FAILED state
 */
@Component
public class EmailCampaignFailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignFailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailCampaignFailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign failure for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email campaign for failure processing")
                .map(this::processEmailCampaignFailure)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for failure processing
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
     * Main business logic for email campaign failure processing
     */
    private EntityWithMetadata<EmailCampaign> processEmailCampaignFailure(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign campaign = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing failure for campaign: {} in state: {}", campaign.getCampaignId(), currentState);

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

        // Calculate partial delivery statistics for failed campaign
        calculatePartialDeliveryStatistics(campaign);

        logger.warn("EmailCampaign {} marked as failed. Partial deliveries - Success: {}, Failed: {}", 
                   campaign.getCampaignId(), campaign.getSuccessfulDeliveries(), campaign.getFailedDeliveries());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Calculates partial delivery statistics for failed campaign
     */
    private void calculatePartialDeliveryStatistics(EmailCampaign campaign) {
        // If no delivery stats are set, assume complete failure
        if (campaign.getSuccessfulDeliveries() == 0 && campaign.getFailedDeliveries() == 0) {
            campaign.setFailedDeliveries(campaign.getTotalSubscribers());
            campaign.setSuccessfulDeliveries(0);
        }

        // Ensure bounces and unsubscribes are set
        if (campaign.getBounces() == null) {
            campaign.setBounces(0);
        }
        
        if (campaign.getUnsubscribes() == null) {
            campaign.setUnsubscribes(0);
        }

        // Set engagement metrics to 0 for failed campaign
        campaign.setOpens(0);
        campaign.setClicks(0);
    }
}
