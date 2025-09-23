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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EmailReportingProcessor - Handles email campaign reporting and final metrics
 * 
 * This processor finalizes email campaigns by calculating final metrics,
 * updating campaign status, and preparing reporting data.
 */
@Component
public class EmailReportingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign reporting for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailCampaign.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EmailCampaign entity wrapper")
                .map(this::processReportingLogic)
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
     * Main business logic for email campaign reporting
     */
    private EntityWithMetadata<EmailCampaign> processReportingLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailCampaign> context) {

        EntityWithMetadata<EmailCampaign> entityWithMetadata = context.entityResponse();
        EmailCampaign entity = entityWithMetadata.entity();

        logger.debug("Processing reporting for campaign: {}", entity.getCampaignId());

        // Finalize campaign metrics
        finalizeCampaignMetrics(entity);
        
        // Update campaign status
        updateCampaignStatus(entity);
        
        // Generate reporting summary
        generateReportingSummary(entity);

        // Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());

        logger.info("EmailCampaign {} reporting completed successfully", entity.getCampaignId());

        return entityWithMetadata;
    }

    /**
     * Finalizes campaign metrics with complete calculations
     */
    private void finalizeCampaignMetrics(EmailCampaign entity) {
        if (entity.getDeliveryResults() != null && !entity.getDeliveryResults().isEmpty()) {
            EmailCampaign.CampaignMetrics metrics = entity.getMetrics();
            if (metrics == null) {
                metrics = new EmailCampaign.CampaignMetrics();
                entity.setMetrics(metrics);
            }

            // Recalculate all metrics for final reporting
            int totalSent = entity.getSuccessCount() != null ? entity.getSuccessCount() : 0;
            int totalRecipients = entity.getRecipientCount() != null ? entity.getRecipientCount() : 0;
            int totalBounces = entity.getFailureCount() != null ? entity.getFailureCount() : 0;
            int totalOpens = 0;
            int totalClicks = 0;

            // Count opens and clicks from delivery results
            for (EmailCampaign.EmailDeliveryResult result : entity.getDeliveryResults()) {
                if (Boolean.TRUE.equals(result.getOpened())) {
                    totalOpens++;
                }
                if (Boolean.TRUE.equals(result.getClicked())) {
                    totalClicks++;
                }
            }

            // Update metrics
            metrics.setTotalOpens(totalOpens);
            metrics.setTotalClicks(totalClicks);
            metrics.setTotalBounces(totalBounces);

            // Calculate rates
            if (totalRecipients > 0) {
                metrics.setDeliveryRate((double) totalSent / totalRecipients);
                metrics.setBounceRate((double) totalBounces / totalRecipients);
            } else {
                metrics.setDeliveryRate(0.0);
                metrics.setBounceRate(0.0);
            }

            if (totalSent > 0) {
                metrics.setOpenRate((double) totalOpens / totalSent);
            } else {
                metrics.setOpenRate(0.0);
            }

            if (totalOpens > 0) {
                metrics.setClickRate((double) totalClicks / totalOpens);
            } else {
                metrics.setClickRate(0.0);
            }

            metrics.setLastUpdated(LocalDateTime.now());

            logger.debug("Final metrics calculated for campaign {}: Delivery: {:.2f}%, Open: {:.2f}%, Click: {:.2f}%",
                        entity.getCampaignId(),
                        metrics.getDeliveryRate() * 100,
                        metrics.getOpenRate() * 100,
                        metrics.getClickRate() * 100);
        }
    }

    /**
     * Updates campaign status based on results
     */
    private void updateCampaignStatus(EmailCampaign entity) {
        if (entity.getStatus() == EmailCampaign.CampaignStatus.SENDING) {
            // Determine final status based on results
            int totalRecipients = entity.getRecipientCount() != null ? entity.getRecipientCount() : 0;
            int successCount = entity.getSuccessCount() != null ? entity.getSuccessCount() : 0;
            int failureCount = entity.getFailureCount() != null ? entity.getFailureCount() : 0;

            if (totalRecipients == 0) {
                entity.setStatus(EmailCampaign.CampaignStatus.FAILED);
                logger.warn("Campaign {} marked as failed: no recipients", entity.getCampaignId());
            } else if (successCount == 0) {
                entity.setStatus(EmailCampaign.CampaignStatus.FAILED);
                logger.warn("Campaign {} marked as failed: no successful sends", entity.getCampaignId());
            } else if (failureCount > successCount) {
                entity.setStatus(EmailCampaign.CampaignStatus.FAILED);
                logger.warn("Campaign {} marked as failed: more failures than successes", entity.getCampaignId());
            } else {
                entity.setStatus(EmailCampaign.CampaignStatus.COMPLETED);
                logger.info("Campaign {} marked as completed successfully", entity.getCampaignId());
            }
        }
    }

    /**
     * Generates a reporting summary for the campaign
     */
    private void generateReportingSummary(EmailCampaign entity) {
        StringBuilder summary = new StringBuilder();
        summary.append("Campaign Report for: ").append(entity.getCampaignName()).append("\n");
        summary.append("Campaign ID: ").append(entity.getCampaignId()).append("\n");
        summary.append("Sent Date: ").append(entity.getSentDate()).append("\n");
        summary.append("Status: ").append(entity.getStatus()).append("\n");
        summary.append("Recipients: ").append(entity.getRecipientCount()).append("\n");
        summary.append("Successful Sends: ").append(entity.getSuccessCount()).append("\n");
        summary.append("Failed Sends: ").append(entity.getFailureCount()).append("\n");

        if (entity.getMetrics() != null) {
            EmailCampaign.CampaignMetrics metrics = entity.getMetrics();
            summary.append("Delivery Rate: ").append(String.format("%.2f%%", metrics.getDeliveryRate() * 100)).append("\n");
            summary.append("Open Rate: ").append(String.format("%.2f%%", metrics.getOpenRate() * 100)).append("\n");
            summary.append("Click Rate: ").append(String.format("%.2f%%", metrics.getClickRate() * 100)).append("\n");
            summary.append("Bounce Rate: ").append(String.format("%.2f%%", metrics.getBounceRate() * 100)).append("\n");
        }

        logger.info("Campaign Summary:\n{}", summary.toString());
    }
}
