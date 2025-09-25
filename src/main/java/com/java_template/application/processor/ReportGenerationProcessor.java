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

import java.time.LocalDateTime;

/**
 * ReportGenerationProcessor - Handles report generation logic
 * Processes report generation requests and sets initial values
 */
@Component
public class ReportGenerationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReportGenerationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report generation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Report.class)
                .validate(this::isValidEntityWithMetadata, "Invalid report entity wrapper")
                .map(this::processReportGenerationLogic)
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
     * Main business logic for report generation
     */
    private EntityWithMetadata<Report> processReportGenerationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Report> context) {

        EntityWithMetadata<Report> entityWithMetadata = context.entityResponse();
        Report report = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing report generation: {} in state: {}", report.getReportName(), currentState);

        // Set generation timestamp if not already set
        if (report.getGenerationDate() == null) {
            report.setGenerationDate(LocalDateTime.now());
        }

        // Set default format if not specified
        if (report.getFormat() == null || report.getFormat().trim().isEmpty()) {
            report.setFormat("PDF"); // Default format
        }

        // Set default parameters if not provided
        if (report.getParameters() == null || report.getParameters().trim().isEmpty()) {
            report.setParameters("{}"); // Empty JSON object
        }

        // Generate file path if not provided
        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            report.setFilePath(generateFilePath(report));
        }

        // Validate data range
        validateDataRange(report);

        logger.info("Report {} generation started by {} with type: {} and format: {}", 
                   report.getReportName(), report.getGeneratedBy(), report.getReportType(), report.getFormat());

        return entityWithMetadata;
    }

    /**
     * Generate file path for report storage
     */
    private String generateFilePath(Report report) {
        String timestamp = report.getGenerationDate().toString().replaceAll("[^0-9]", "");
        String extension = getFileExtension(report.getFormat());
        return String.format("/reports/%s/%s_%s.%s", 
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
     * Validate and adjust data range if necessary
     */
    private void validateDataRange(Report report) {
        String dataRange = report.getDataRange();
        if (dataRange != null && dataRange.contains(",")) {
            String[] parts = dataRange.split(",");
            if (parts.length == 2) {
                try {
                    // Parse dates to validate format (simplified)
                    String startDate = parts[0].trim();
                    String endDate = parts[1].trim();
                    
                    // Check if end date is after start date (simplified check)
                    if (endDate.compareTo(startDate) < 0) {
                        logger.warn("End date is before start date in data range: {}", dataRange);
                        // Swap dates
                        report.setDataRange(endDate + "," + startDate);
                    }
                    
                    // Check if range is not more than 2 years
                    int startYear = Integer.parseInt(startDate.substring(0, 4));
                    int endYear = Integer.parseInt(endDate.substring(0, 4));
                    if (endYear - startYear > 2) {
                        logger.warn("Data range exceeds 2 years: {}", dataRange);
                        // Adjust end date to 2 years from start
                        String adjustedEndDate = (startYear + 2) + endDate.substring(4);
                        report.setDataRange(startDate + "," + adjustedEndDate);
                    }
                } catch (Exception e) {
                    logger.warn("Error validating data range: {}", dataRange, e);
                }
            }
        }
    }
}
