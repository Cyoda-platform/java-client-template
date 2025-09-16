package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Processor for email campaign finalization workflow transition.
 * Handles the finalize transition (sent → completed).
 * 
 * Business Logic:
 * - Waits for initial analytics (opens, clicks) - 1 hour delay
 * - Collects open tracking data
 * - Collects click tracking data
 * - Collects unsubscribe data from this campaign
 * - Sets openCount, clickCount, unsubscribeCount
 * - Calculates engagement metrics
 * - Generates campaign report
 * - Stores analytics data
 */
@Component
public class EmailCampaignFinalizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignFinalizationProcessor.class);
    private final ProcessorSerializer serializer;

    public EmailCampaignFinalizationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("EmailCampaignFinalizationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing email campaign finalization for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .validate(this::validateFinalizationData, "Invalid finalization data")
            .map(ctx -> {
                EmailCampaign campaign = ctx.entity();
                
                // Simulate waiting for analytics (in real implementation, this would be handled differently)
                logger.debug("Collecting analytics data for campaign: {}", campaign.getCampaignName());
                
                // Collect open tracking data (simulated)
                int openCount = collectOpenTrackingData(campaign);
                campaign.setOpenCount(openCount);
                
                // Collect click tracking data (simulated)
                int clickCount = collectClickTrackingData(campaign);
                campaign.setClickCount(clickCount);
                
                // Collect unsubscribe data from this campaign (simulated)
                int unsubscribeCount = collectUnsubscribeData(campaign);
                campaign.setUnsubscribeCount(unsubscribeCount);
                
                // Calculate engagement metrics
                double openRate = calculateOpenRate(campaign);
                double clickRate = calculateClickRate(campaign);
                double unsubscribeRate = calculateUnsubscribeRate(campaign);
                
                // Generate campaign report (simplified logging)
                generateCampaignReport(campaign, openRate, clickRate, unsubscribeRate);
                
                logger.info("Email campaign finalized: {} - Opens: {}, Clicks: {}, Unsubscribes: {}", 
                           campaign.getCampaignName(), openCount, clickCount, unsubscribeCount);
                
                return campaign;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignFinalizationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates finalization data.
     */
    private boolean validateFinalizationData(EmailCampaign campaign) {
        if (campaign == null) {
            logger.warn("Finalization failed: EmailCampaign is null");
            return false;
        }
        
        // Campaign must be sent
        if (campaign.getSentDate() == null) {
            logger.warn("Finalization failed: Campaign has not been sent");
            return false;
        }
        
        // Successful deliveries should be set
        if (campaign.getSuccessfulDeliveries() == null || campaign.getSuccessfulDeliveries() <= 0) {
            logger.warn("Finalization failed: No successful deliveries to analyze");
            return false;
        }
        
        logger.debug("Finalization data validation passed");
        return true;
    }

    /**
     * Collects open tracking data (simulated).
     */
    private int collectOpenTrackingData(EmailCampaign campaign) {
        // Simulate open rate of 50-70% of successful deliveries
        int successfulDeliveries = campaign.getSuccessfulDeliveries();
        double openRate = 0.5 + (ThreadLocalRandom.current().nextDouble() * 0.2); // 50-70%
        return (int) (successfulDeliveries * openRate);
    }

    /**
     * Collects click tracking data (simulated).
     */
    private int collectClickTrackingData(EmailCampaign campaign) {
        // Simulate click rate of 5-15% of opens
        int openCount = campaign.getOpenCount();
        if (openCount == 0) {
            return 0;
        }
        double clickRate = 0.05 + (ThreadLocalRandom.current().nextDouble() * 0.1); // 5-15%
        return (int) (openCount * clickRate);
    }

    /**
     * Collects unsubscribe data from this campaign (simulated).
     */
    private int collectUnsubscribeData(EmailCampaign campaign) {
        // Simulate unsubscribe rate of 0.5-2% of successful deliveries
        int successfulDeliveries = campaign.getSuccessfulDeliveries();
        double unsubscribeRate = 0.005 + (ThreadLocalRandom.current().nextDouble() * 0.015); // 0.5-2%
        return (int) (successfulDeliveries * unsubscribeRate);
    }

    /**
     * Calculates open rate.
     */
    private double calculateOpenRate(EmailCampaign campaign) {
        if (campaign.getSuccessfulDeliveries() == 0) {
            return 0.0;
        }
        return (campaign.getOpenCount() * 100.0) / campaign.getSuccessfulDeliveries();
    }

    /**
     * Calculates click rate.
     */
    private double calculateClickRate(EmailCampaign campaign) {
        if (campaign.getOpenCount() == 0) {
            return 0.0;
        }
        return (campaign.getClickCount() * 100.0) / campaign.getOpenCount();
    }

    /**
     * Calculates unsubscribe rate.
     */
    private double calculateUnsubscribeRate(EmailCampaign campaign) {
        if (campaign.getSuccessfulDeliveries() == 0) {
            return 0.0;
        }
        return (campaign.getUnsubscribeCount() * 100.0) / campaign.getSuccessfulDeliveries();
    }

    /**
     * Generates campaign report.
     */
    private void generateCampaignReport(EmailCampaign campaign, double openRate, double clickRate, double unsubscribeRate) {
        logger.info("Campaign Report for: {}", campaign.getCampaignName());
        logger.info("  Total Subscribers: {}", campaign.getTotalSubscribers());
        logger.info("  Successful Deliveries: {}", campaign.getSuccessfulDeliveries());
        logger.info("  Failed Deliveries: {}", campaign.getFailedDeliveries());
        logger.info("  Opens: {} ({:.2f}%)", campaign.getOpenCount(), openRate);
        logger.info("  Clicks: {} ({:.2f}%)", campaign.getClickCount(), clickRate);
        logger.info("  Unsubscribes: {} ({:.2f}%)", campaign.getUnsubscribeCount(), unsubscribeRate);
        
        // In a real implementation, this would store the report in a database or file
    }
}
