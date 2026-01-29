package com.java_template.application.processor;

import com.java_template.application.entity.product_performance_report.version_1.ProductPerformanceReport;
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
 * ReportGenerationProcessor
 * Generates PDF report content from analyzed metrics
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
        logger.info("Processing report generation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ProductPerformanceReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processReportGeneration)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(
            EntityWithMetadata<ProductPerformanceReport> entityWithMetadata) {
        ProductPerformanceReport entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<ProductPerformanceReport> processReportGeneration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ProductPerformanceReport> context) {

        EntityWithMetadata<ProductPerformanceReport> entityWithMetadata = context.entityResponse();
        ProductPerformanceReport report = entityWithMetadata.entity();

        logger.debug("Generating report for: {}", report.getReportId());

        String reportContent = generateReportContent(report);
        report.setReportContent(reportContent);

        String summary = generateReportSummary(report);
        report.setReportSummary(summary);

        report.setUpdatedAt(LocalDateTime.now());
        logger.info("Report generation completed for: {}", report.getReportId());
        return entityWithMetadata;
    }

    private String generateReportContent(ProductPerformanceReport report) {
        StringBuilder content = new StringBuilder();
        content.append("=== PRODUCT PERFORMANCE REPORT ===\n\n");
        content.append("Report Week: ").append(report.getReportWeek()).append("\n");
        content.append("Generated: ").append(report.getReportGeneratedAt()).append("\n\n");

        content.append("EXECUTIVE SUMMARY\n");
        content.append("-----------------\n");
        content.append("Total Sales Volume: ").append(report.getTotalSalesVolume()).append(" units\n");
        content.append("Total Revenue: $").append(String.format("%.2f", report.getTotalRevenue())).append("\n");
        content.append("Average Inventory Turnover: ").append(String.format("%.2f", report.getAverageInventoryTurnover())).append("\n\n");

        content.append("TOP SELLING PRODUCTS\n");
        content.append("-------------------\n");
        if (report.getTopSellingProducts() != null) {
            report.getTopSellingProducts().forEach(p -> content.append("- ").append(p).append("\n"));
        }
        content.append("\n");

        content.append("CATEGORY ANALYSIS\n");
        content.append("-----------------\n");
        if (report.getCategoryAnalyses() != null) {
            report.getCategoryAnalyses().forEach(ca -> {
                content.append("Category: ").append(ca.getCategoryName()).append("\n");
                content.append("  Products: ").append(ca.getTotalProducts()).append("\n");
                content.append("  Revenue: $").append(String.format("%.2f", ca.getCategoryRevenue())).append("\n");
                content.append("  Avg Turnover: ").append(String.format("%.2f", ca.getAverageTurnover())).append("\n\n");
            });
        }

        content.append("RESTOCKING RECOMMENDATIONS\n");
        content.append("--------------------------\n");
        if (report.getRestockingRecommendations() != null) {
            report.getRestockingRecommendations().forEach(rec -> {
                content.append("- ").append(rec.getProductName()).append(" [").append(rec.getUrgency().toUpperCase()).append("]\n");
                content.append("  Recommended Qty: ").append(rec.getRecommendedQuantity()).append("\n");
                content.append("  Reason: ").append(rec.getReason()).append("\n\n");
            });
        }

        return content.toString();
    }

    private String generateReportSummary(ProductPerformanceReport report) {
        return String.format(
                "Weekly Performance Summary for %s: Total Revenue: $%.2f | Sales Volume: %.0f units | Top Product: %s",
                report.getReportWeek(),
                report.getTotalRevenue(),
                report.getTotalSalesVolume(),
                report.getTopSellingProducts() != null && !report.getTopSellingProducts().isEmpty()
                        ? report.getTopSellingProducts().get(0)
                        : "N/A"
        );
    }
}

