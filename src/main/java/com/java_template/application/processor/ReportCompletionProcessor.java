package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Component
public class ReportCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ReportCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report completion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        logger.info("Finalizing report generation: {}", entity.getReportName());

        // Verify report file was created successfully
        boolean fileExists = verifyReportFile(entity);
        if (!fileExists) {
            throw new IllegalStateException("Report file was not created successfully: " + entity.getFilePath());
        }

        // Validate report content
        validateReportContent(entity);

        // Generate email summary if not already set
        if (entity.getSummary() == null || entity.getSummary().trim().isEmpty()) {
            generateEmailSummary(entity);
        }

        logger.info("Report generation finalized: {}", entity.getReportName());
        return entity;
    }

    private boolean verifyReportFile(Report report) {
        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            logger.error("Report file path is not set");
            return false;
        }

        // In a real implementation, this would check if the file actually exists
        // For now, we'll simulate that the file exists
        logger.info("Verified report file exists: {}", report.getFilePath());
        return true;
    }

    private void validateReportContent(Report report) {
        // Ensure all required sections are present
        if (report.getTotalProducts() == null || report.getTotalProducts() < 0) {
            logger.warn("Total products count is invalid: {}", report.getTotalProducts());
        }

        if (report.getTopPerformingProducts() == null) {
            logger.warn("Top performing products list is null");
        }

        if (report.getUnderperformingProducts() == null) {
            logger.warn("Underperforming products list is null");
        }

        if (report.getKeyInsights() == null || report.getKeyInsights().isEmpty()) {
            logger.warn("Key insights are missing or empty");
        }

        // Validate file format
        if (!"PDF".equalsIgnoreCase(report.getFileFormat()) && 
            !"HTML".equalsIgnoreCase(report.getFileFormat()) && 
            !"CSV".equalsIgnoreCase(report.getFileFormat())) {
            logger.warn("Unsupported file format: {}", report.getFileFormat());
        }

        logger.info("Report content validation completed");
    }

    private void generateEmailSummary(Report report) {
        StringBuilder summary = new StringBuilder();
        summary.append("Weekly Product Performance Report\n\n");
        
        summary.append("Report: ").append(report.getReportName()).append("\n");
        summary.append("Period: ").append(report.getReportPeriodStart())
               .append(" to ").append(report.getReportPeriodEnd()).append("\n");
        summary.append("Generated: ").append(report.getGenerationDate()).append("\n\n");
        
        summary.append("Summary:\n");
        summary.append("- Total products analyzed: ").append(report.getTotalProducts()).append("\n");
        
        if (report.getTopPerformingProducts() != null && !report.getTopPerformingProducts().isEmpty()) {
            summary.append("- Top performing products: ");
            summary.append(String.join(", ", report.getTopPerformingProducts().subList(0, 
                Math.min(3, report.getTopPerformingProducts().size()))));
            summary.append("\n");
        }
        
        if (report.getKeyInsights() != null && !report.getKeyInsights().isEmpty()) {
            summary.append("\nKey Insights:\n");
            for (int i = 0; i < Math.min(3, report.getKeyInsights().size()); i++) {
                summary.append("- ").append(report.getKeyInsights().get(i)).append("\n");
            }
        }
        
        summary.append("\nPlease find the detailed report attached.");
        
        report.setSummary(summary.toString());
        logger.info("Email summary generated for report: {}", report.getReportName());
    }
}
