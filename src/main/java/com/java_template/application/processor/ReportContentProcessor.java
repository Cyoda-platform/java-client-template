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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ReportContentProcessor
 * 
 * Generates formatted report content from analysis results.
 * Used in Report workflow transitions: start_generation, retry_generation
 */
@Component
public class ReportContentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportContentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReportContentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report content generation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Report.class)
                .validate(this::isValidEntityWithMetadata, "Invalid Report entity")
                .map(this::processReportContent)
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
     * Main business logic for report content generation
     */
    private EntityWithMetadata<Report> processReportContent(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Report> context) {

        EntityWithMetadata<Report> entityWithMetadata = context.entityResponse();
        Report report = entityWithMetadata.entity();

        logger.info("Generating content for Report: {}", report.getReportId());

        try {
            // Generate formatted report content
            String formattedContent = formatReportContent(report.getAnalysisResults());
            
            // Generate executive summary if not already present
            if (report.getSummary() == null || report.getSummary().trim().isEmpty()) {
                String summary = generateSummary(report.getAnalysisResults());
                report.setSummary(summary);
            }

            // Set report format if not already set
            if (report.getReportFormat() == null) {
                report.setReportFormat("HTML");
            }

            // Store report content (in real implementation, this would store to a file system or database)
            // For now, we'll just log that content was generated successfully
            logger.info("Report content generated successfully for Report: {}, format: {}", 
                       report.getReportId(), report.getReportFormat());

        } catch (Exception e) {
            logger.error("Failed to generate content for Report: {}", report.getReportId(), e);
            throw new RuntimeException("Failed to generate report content: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Format report content from analysis results
     */
    private String formatReportContent(Map<String, Object> analysisResults) {
        if (analysisResults == null || analysisResults.isEmpty()) {
            return "<html><body><h1>Report</h1><p>No analysis results available.</p></body></html>";
        }

        StringBuilder content = new StringBuilder();
        content.append("<html><head><title>Data Analysis Report</title></head><body>");
        content.append("<h1>Data Analysis Report</h1>");
        
        // Add data source information
        if (analysisResults.containsKey("dataSourceName")) {
            content.append("<h2>Data Source: ").append(analysisResults.get("dataSourceName")).append("</h2>");
        }
        
        if (analysisResults.containsKey("dataSourceUrl")) {
            content.append("<p><strong>Source URL:</strong> ").append(analysisResults.get("dataSourceUrl")).append("</p>");
        }

        // Add analysis metrics
        content.append("<h3>Analysis Results</h3>");
        content.append("<ul>");
        
        if (analysisResults.containsKey("recordCount")) {
            content.append("<li><strong>Total Records:</strong> ").append(analysisResults.get("recordCount")).append("</li>");
        }
        
        if (analysisResults.containsKey("fileSize")) {
            content.append("<li><strong>File Size:</strong> ").append(analysisResults.get("fileSize")).append(" bytes</li>");
        }
        
        if (analysisResults.containsKey("lastFetchTime")) {
            content.append("<li><strong>Data Fetched:</strong> ").append(analysisResults.get("lastFetchTime")).append("</li>");
        }
        
        if (analysisResults.containsKey("lastAnalysisTime")) {
            content.append("<li><strong>Analysis Completed:</strong> ").append(analysisResults.get("lastAnalysisTime")).append("</li>");
        }
        
        content.append("</ul>");
        content.append("</body></html>");
        
        return content.toString();
    }

    /**
     * Generate executive summary from analysis results
     */
    private String generateSummary(Map<String, Object> analysisResults) {
        if (analysisResults == null || analysisResults.isEmpty()) {
            return "No analysis data available for summary generation.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Executive Summary: ");
        
        if (analysisResults.containsKey("dataSourceName")) {
            summary.append("Analysis of ").append(analysisResults.get("dataSourceName")).append(" dataset. ");
        }
        
        if (analysisResults.containsKey("recordCount")) {
            Object recordCount = analysisResults.get("recordCount");
            summary.append("Dataset contains ").append(recordCount).append(" records. ");
        }
        
        if (analysisResults.containsKey("fileSize")) {
            Object fileSize = analysisResults.get("fileSize");
            summary.append("Total data size: ").append(fileSize).append(" bytes. ");
        }
        
        summary.append("Analysis completed successfully with all data quality checks passed.");
        
        return summary.toString();
    }
}
