package com.example.application.processor;

import com.example.application.entity.product_performance_report.version_1.ProductPerformanceReport;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * ReportGenerationProcessor
 * Generates PDF report from analyzed metrics
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

        // Generate report content
        String reportContent = generateReportContent(report);
        report.setReportContent(reportContent);

        // Generate summary
        String summary = generateReportSummary(report);
        report.setReportSummary(summary);

        report.setUpdatedAt(LocalDateTime.now());

        logger.info("Report generation completed for: {}", report.getReportId());
        return entityWithMetadata;
    }

    private String generateReportContent(ProductPerformanceReport report) {
        StringBuilder content = new StringBuilder();

        content.append("=== WEEKLY PRODUCT PERFORMANCE REPORT ===\n");
        content.append("Report Week: ").append(report.getReportWeek()).append("\n");
        content.append("Generated: ").append(report.getReportGeneratedAt()).append("\n\n");

        content.append("--- EXECUTIVE SUMMARY ---\n");
        content.append("Total Sales Volume: ").append(report.getTotalSalesVolume()).append(" units\n");
        content.append("Total Revenue: $").append(String.format("%.2f", report.getTotalRevenue())).append("\n");
        content.append("Average Inventory Turnover: ").append(String.format("%.2f", report.getAverageInventoryTurnover())).append("\n\n");

        content.append("--- TOP SELLING PRODUCTS ---\n");
        if (report.getTopSellingProducts() != null) {
            report.getTopSellingProducts().forEach(p -> content.append("• ").append(p).append("\n"));
        }
        content.append("\n");

        content.append("--- CATEGORY ANALYSIS ---\n");
        if (report.getCategoryAnalyses() != null) {
            report.getCategoryAnalyses().forEach(cat -> {
                content.append("Category: ").append(cat.getCategoryName()).append("\n");
                content.append("  Products: ").append(cat.getTotalProducts()).append("\n");
                content.append("  Revenue: $").append(String.format("%.2f", cat.getCategoryRevenue())).append("\n");
                content.append("  Avg Turnover: ").append(String.format("%.2f", cat.getAverageTurnover())).append("\n\n");
            });
        }

        content.append("--- RESTOCKING RECOMMENDATIONS ---\n");
        if (report.getRestockingRecommendations() != null && !report.getRestockingRecommendations().isEmpty()) {
            report.getRestockingRecommendations().forEach(rec -> {
                content.append("• ").append(rec.getProductName()).append(" (").append(rec.getUrgency()).append(")\n");
                content.append("  Recommended Qty: ").append(rec.getRecommendedQuantity()).append("\n");
                content.append("  Reason: ").append(rec.getReason()).append("\n");
            });
        } else {
            content.append("No restocking needed at this time.\n");
        }

        return content.toString();
    }

    private String generateReportSummary(ProductPerformanceReport report) {
        return String.format(
                "Weekly report for %s shows %d total units sold with $%.2f revenue. " +
                "Top performers: %s. Average inventory turnover: %.2f.",
                report.getReportWeek(),
                report.getTotalSalesVolume().intValue(),
                report.getTotalRevenue(),
                report.getTopSellingProducts() != null && !report.getTopSellingProducts().isEmpty()
                        ? report.getTopSellingProducts().stream().limit(3).collect(Collectors.joining(", "))
                        : "N/A",
                report.getAverageInventoryTurnover()
        );
    }
}

