package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
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

/**
 * ReportCompletionProcessor - Handles report completion logic
 * Processes report completion and finalizes report data
 */
@Component
public class ReportCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReportCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Report.class)
                .validate(this::isValidEntityWithMetadata, "Invalid report entity wrapper")
                .map(this::processReportCompletionLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Report> entityWithMetadata) {
        Report entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for report completion
     */
    private EntityWithMetadata<Report> processReportCompletionLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Report> context) {

        EntityWithMetadata<Report> entityWithMetadata = context.entityResponse();
        Report report = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing report completion: {} in state: {}", report.getReportName(), currentState);

        // Finalize report data
        finalizeReportData(report);

        // Validate report completion
        boolean isComplete = validateReportCompletion(report);

        if (isComplete) {
            logger.info("Report {} completed successfully by {} with format: {}", 
                       report.getReportName(), report.getGeneratedBy(), report.getFormat());
        } else {
            logger.warn("Report {} completion validation failed", report.getReportName());
        }

        return entityWithMetadata;
    }

    /**
     * Finalize report data and ensure all required fields are set
     */
    private void finalizeReportData(Report report) {
        // Ensure file path is set
        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            report.setFilePath(generateFinalFilePath(report));
        }

        // Ensure parameters are valid JSON
        if (!isValidJson(report.getParameters())) {
            logger.warn("Invalid JSON parameters for report: {}", report.getReportName());
            report.setParameters("{}"); // Set to empty JSON object
        }

        // Log completion details
        logReportDetails(report);
    }

    /**
     * Validate that the report is ready for completion
     */
    private boolean validateReportCompletion(Report report) {
        boolean isValid = true;

        // Check all required fields are present
        if (report.getReportName() == null || report.getReportName().trim().isEmpty()) {
            logger.warn("Report name is missing");
            isValid = false;
        }

        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            logger.warn("Report file path is missing");
            isValid = false;
        }

        if (report.getGeneratedBy() == null || report.getGeneratedBy().trim().isEmpty()) {
            logger.warn("Report generator is missing");
            isValid = false;
        }

        if (report.getGenerationDate() == null) {
            logger.warn("Report generation date is missing");
            isValid = false;
        }

        // Validate report type
        if (!isValidReportType(report.getReportType())) {
            logger.warn("Invalid report type: {}", report.getReportType());
            isValid = false;
        }

        // Validate format
        if (!isValidFormat(report.getFormat())) {
            logger.warn("Invalid report format: {}", report.getFormat());
            isValid = false;
        }

        return isValid;
    }

    /**
     * Generate final file path for completed report
     */
    private String generateFinalFilePath(Report report) {
        String timestamp = report.getGenerationDate().toString().replaceAll("[^0-9]", "");
        String extension = getFileExtension(report.getFormat());
        return String.format("/reports/completed/%s/%s_%s.%s", 
                           report.getReportType().toLowerCase(), 
                           report.getReportName().replaceAll("[^a-zA-Z0-9]", "_"),
                           timestamp.substring(0, Math.min(timestamp.length(), 14)),
                           extension);
    }

    /**
     * Get file extension based on format
     */
    private String getFileExtension(String format) {
        switch (format.toUpperCase()) {
            case "PDF":
                return "pdf";
            case "CSV":
                return "csv";
            case "JSON":
                return "json";
            default:
                return "txt";
        }
    }

    /**
     * Log report completion details
     */
    private void logReportDetails(Report report) {
        logger.info("Report completion details:");
        logger.info("  Name: {}", report.getReportName());
        logger.info("  Type: {}", report.getReportType());
        logger.info("  Format: {}", report.getFormat());
        logger.info("  Generated by: {}", report.getGeneratedBy());
        logger.info("  Data range: {}", report.getDataRange());
        logger.info("  File path: {}", report.getFilePath());
    }

    /**
     * Simple JSON validation
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        json = json.trim();
        return (json.startsWith("{") && json.endsWith("}")) || 
               (json.startsWith("[") && json.endsWith("]"));
    }

    /**
     * Validates if the report type is one of the allowed values
     */
    private boolean isValidReportType(String type) {
        return "SUBMISSION_STATUS".equals(type) || 
               "USER_ACTIVITY".equals(type) || 
               "PERFORMANCE_METRICS".equals(type);
    }

    /**
     * Validates if the format is one of the allowed values
     */
    private boolean isValidFormat(String format) {
        return "PDF".equals(format) || 
               "CSV".equals(format) || 
               "JSON".equals(format);
    }
}
