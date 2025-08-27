package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
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

import java.time.Instant;
import java.util.List;

@Component
public class PrepareDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PrepareDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PrepareDeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        Report entity = context.entity();
        try {
            if (entity == null) {
                logger.error("Report entity is null in context");
                return null;
            }

            // Ensure generatedAt exists (defensive). Validate would normally ensure this.
            if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                entity.setGeneratedAt(Instant.now().toString());
                logger.debug("Set generatedAt for report to now: {}", entity.getGeneratedAt());
            }

            // Basic guard: popularTitles should be present for preparing delivery.
            List<Report.BookSummary> popular = entity.getPopularTitles();
            if (popular == null || popular.isEmpty()) {
                logger.error("Report {} has no popularTitles; cannot prepare delivery. Marking as FAILED", entity.getReportId());
                entity.setStatus("FAILED");
                return entity;
            }

            // Normalize format
            String format = entity.getFormat();
            if (format == null || format.isBlank()) {
                format = "inline";
                entity.setFormat(format);
            }

            // Ensure there is a human-friendly titleInsights summary. If missing, synthesize a short one.
            if (entity.getTitleInsights() == null || entity.getTitleInsights().isBlank()) {
                StringBuilder insights = new StringBuilder();
                insights.append("Report summary for ")
                        .append(entity.getPeriodStart()).append(" to ").append(entity.getPeriodEnd())
                        .append(". Total books: ").append(entity.getTotalBooks() != null ? entity.getTotalBooks() : 0)
                        .append(", total pages: ").append(entity.getTotalPageCount() != null ? entity.getTotalPageCount() : 0)
                        .append(".");
                entity.setTitleInsights(insights.toString());
            }

            // Render a short inline preview (append to titleInsights) when inline format is requested
            if ("inline".equalsIgnoreCase(format)) {
                StringBuilder preview = new StringBuilder();
                preview.append("\n\nTop popular titles:\n");
                int limit = Math.min(3, popular.size());
                for (int i = 0; i < limit; i++) {
                    Report.BookSummary bs = popular.get(i);
                    if (bs == null) continue;
                    preview.append(i + 1).append(". ").append(bs.getTitle() != null ? bs.getTitle() : "Untitled");
                    if (bs.getPageCount() != null) preview.append(" (").append(bs.getPageCount()).append(" pages)");
                    if (bs.getPublishDate() != null) preview.append(" - ").append(bs.getPublishDate());
                    if (bs.getExcerpt() != null && !bs.getExcerpt().isBlank()) {
                        preview.append(" - ").append(bs.getExcerpt());
                    }
                    preview.append("\n");
                }
                // Append preview to titleInsights so downstream SendReportProcessor can use it for the email body
                entity.setTitleInsights(entity.getTitleInsights() + preview.toString());
            } else {
                // For attachment format, ensure the titleInsights contains a clear summary (already ensured above).
                // Additional attachment preparation would occur in a dedicated attachment generator; here we only mark status.
                logger.debug("Report {} is prepared as attachment", entity.getReportId());
            }

            // Mark the report as ready to be sent
            entity.setStatus("SENDING");
            logger.info("Report {} prepared for delivery with format '{}', status set to SENDING", entity.getReportId(), entity.getFormat());

            return entity;
        } catch (Exception ex) {
            logger.error("Error while preparing report for delivery: {}", ex.getMessage(), ex);
            if (entity != null) {
                entity.setStatus("FAILED");
            }
            return entity;
        }
    }
}