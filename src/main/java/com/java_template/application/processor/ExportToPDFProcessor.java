package com.java_template.application.processor;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.nio.charset.StandardCharsets;

@Component
public class ExportToPDFProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExportToPDFProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExportToPDFProcessor(SerializerFactory serializerFactory,
                                EntityService entityService,
                                ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyReport for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyReport.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyReport entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport entity = context.entity();

        try {
            // Basic sanity checks using only existing getters/setters
            String reportId = entity.getReportId();
            if (reportId == null || reportId.isBlank()) {
                logger.error("WeeklyReport missing reportId - cannot export to PDF");
                entity.setStatus("FAILED");
                return entity;
            }

            // Ensure generatedAt exists; if not, set to current time
            if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                String now = Instant.now().toString();
                entity.setGeneratedAt(now);
                logger.debug("generatedAt was blank, set to {}", now);
            }

            // Compose a simple HTML representation from available fields (summary, weekStart)
            StringBuilder htmlBuilder = new StringBuilder();
            htmlBuilder.append("<html><head><meta charset=\"UTF-8\"><title>Weekly Report</title></head><body>");
            htmlBuilder.append("<h1>Weekly Report: ").append(escape(entity.getReportId())).append("</h1>");
            htmlBuilder.append("<p><strong>Week Start:</strong> ").append(escape(entity.getWeekStart())).append("</p>");
            htmlBuilder.append("<p><strong>Generated At:</strong> ").append(escape(entity.getGeneratedAt())).append("</p>");
            if (entity.getSummary() != null) {
                htmlBuilder.append("<h2>Summary</h2><p>").append(escape(entity.getSummary())).append("</p>");
            } else {
                htmlBuilder.append("<h2>Summary</h2><p>No summary available.</p>");
            }
            htmlBuilder.append("</body></html>");
            String htmlContent = htmlBuilder.toString();

            // Simulate PDF conversion: produce byte[] representing PDF content.
            // (In a real implementation we'd call a PDF library; here we create a simple byte array placeholder)
            byte[] pdfBytes = ("PDF_PLACEHOLDER_FOR_REPORT:" + reportId + "\nHTML_CONTENT:\n" + htmlContent)
                    .getBytes(StandardCharsets.UTF_8);

            // Simulate upload to file store by generating an attachment URL for the report.
            // Per rules we must not update the persisted entity via entityService here; just set fields on the entity.
            String attachmentUrl = "https://filestore/reports/" + sanitizeForPath(reportId) + ".pdf";
            entity.setAttachmentUrl(attachmentUrl);

            // Mark report as READY
            entity.setStatus("READY");

            logger.info("Exported WeeklyReport {} to PDF placeholder and set attachmentUrl={}", reportId, attachmentUrl);

            // Optionally, we could persist derived artifacts (like a File entity) via entityService.
            // The current entity (WeeklyReport) must not be added/updated via entityService — it will be persisted automatically by Cyoda.

            return entity;
        } catch (Exception ex) {
            logger.error("Failed to export WeeklyReport to PDF: {}", ex.getMessage(), ex);
            entity.setStatus("FAILED");
            return entity;
        }
    }

    // Simple HTML-escaping to avoid malformed HTML when inserting raw text (no external libs)
    private String escape(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    // Sanitize reportId for use in a URL/path segment
    private String sanitizeForPath(String input) {
        if (input == null) return "report";
        return input.replaceAll("[^A-Za-z0-9\\-_\\.]", "_");
    }
}