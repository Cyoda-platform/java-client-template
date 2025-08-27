package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        // Determine recipients from WeeklyJob entities (aggregate recipients across jobs)
        Set<String> recipients = new HashSet<>();
        try {
            ArrayNode jobsArray = entityService.getItems(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION)
            ).join();

            if (jobsArray != null) {
                for (int i = 0; i < jobsArray.size(); i++) {
                    try {
                        ObjectNode jobNode = (ObjectNode) jobsArray.get(i);
                        WeeklyJob job = objectMapper.treeToValue(jobNode, WeeklyJob.class);
                        List<String> jobRecipients = job.getRecipients();
                        if (jobRecipients != null) {
                            for (String r : jobRecipients) {
                                if (r != null && !r.isBlank()) {
                                    recipients.add(r.trim());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.debug("Ignoring invalid WeeklyJob element at index {}: {}", i, ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to retrieve WeeklyJob entities to determine recipients: {}", ex.getMessage());
        }

        // Prepare basic validation before sending
        if (recipients.isEmpty()) {
            logger.warn("No recipients found for report {}. Marking as FAILED.", report.getReportId());
            report.setStatus("FAILED");
            // Do not set sentAt when failed
            return report;
        }

        // Render a simple inline email body (simulation)
        StringBuilder body = new StringBuilder();
        body.append("Report: ").append(report.getReportId()).append("\n");
        body.append("Period: ").append(report.getPeriodStart()).append(" -> ").append(report.getPeriodEnd()).append("\n");
        body.append("Generated At: ").append(report.getGeneratedAt()).append("\n");
        body.append("Total Books: ").append(report.getTotalBooks()).append("\n");
        body.append("Total Pages: ").append(report.getTotalPageCount()).append("\n\n");
        if (report.getTitleInsights() != null && !report.getTitleInsights().isBlank()) {
            body.append("Insights: ").append(report.getTitleInsights()).append("\n\n");
        }
        if (report.getPopularTitles() != null && !report.getPopularTitles().isEmpty()) {
            body.append("Top Titles:\n");
            int count = 0;
            for (Report.BookSummary bs : report.getPopularTitles()) {
                count++;
                body.append(count).append(". ").append(bs.getTitle());
                if (bs.getPageCount() != null) body.append(" (").append(bs.getPageCount()).append(" pages)");
                if (bs.getPublishDate() != null) body.append(" - ").append(bs.getPublishDate());
                body.append("\n");
                if (bs.getExcerpt() != null) body.append("   Excerpt: ").append(bs.getExcerpt()).append("\n");
                if (bs.getDescription() != null) body.append("   Description: ").append(bs.getDescription()).append("\n");
                body.append("\n");
                if (count >= 10) break; // avoid extremely long bodies
            }
        }

        // Simulate sending email - in this processor we only mark result based on simulated send
        try {
            // In real implementation here we'd call an email service.
            // For now, log recipients and body and mark as SENT.
            logger.info("Sending report '{}' to {} recipient(s): {}", report.getReportId(), recipients.size(), recipients);
            logger.debug("Email body:\n{}", body.toString());

            report.setStatus("SENT");
            report.setSentAt(Instant.now().toString());
            // Cyoda will persist this entity automatically; do not call updateItem for this entity.
        } catch (Exception ex) {
            logger.error("Failed to send report '{}': {}", report.getReportId(), ex.getMessage());
            report.setStatus("FAILED");
            // Do not set sentAt when failed
        }

        return report;
    }
}