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
import java.util.*;
import java.util.stream.Collectors;

/**
 * AnalysisProcessor
 * Computes KPIs and analyzes product performance metrics
 */
@Component
public class AnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing analysis for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ProductPerformanceReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processAnalysis)
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

    private EntityWithMetadata<ProductPerformanceReport> processAnalysis(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ProductPerformanceReport> context) {

        EntityWithMetadata<ProductPerformanceReport> entityWithMetadata = context.entityResponse();
        ProductPerformanceReport report = entityWithMetadata.entity();

        logger.debug("Analyzing metrics for report: {}", report.getReportId());

        List<ProductPerformanceReport.ProductMetric> metrics = report.getProductMetrics();
        if (metrics == null || metrics.isEmpty()) {
            logger.warn("No metrics available for analysis");
            return entityWithMetadata;
        }

        // Compute KPIs
        double totalSalesVolume = metrics.stream()
                .mapToInt(ProductPerformanceReport.ProductMetric::getSalesVolume)
                .sum();
        double totalRevenue = metrics.stream()
                .mapToDouble(ProductPerformanceReport.ProductMetric::getRevenue)
                .sum();
        double averageTurnover = metrics.stream()
                .mapToDouble(ProductPerformanceReport.ProductMetric::getInventoryTurnover)
                .average()
                .orElse(0.0);

        report.setTotalSalesVolume(totalSalesVolume);
        report.setTotalRevenue(totalRevenue);
        report.setAverageInventoryTurnover(averageTurnover);

        // Identify top selling products
        List<String> topProducts = metrics.stream()
                .sorted(Comparator.comparingInt(ProductPerformanceReport.ProductMetric::getSalesVolume).reversed())
                .limit(5)
                .map(ProductPerformanceReport.ProductMetric::getProductName)
                .collect(Collectors.toList());
        report.setTopSellingProducts(topProducts);

        // Generate category analyses
        List<ProductPerformanceReport.CategoryAnalysis> categoryAnalyses = generateCategoryAnalyses(metrics);
        report.setCategoryAnalyses(categoryAnalyses);

        // Generate restocking recommendations
        List<ProductPerformanceReport.RestockingRecommendation> recommendations = generateRestockingRecommendations(metrics);
        report.setRestockingRecommendations(recommendations);

        report.setUpdatedAt(LocalDateTime.now());
        logger.info("Analysis completed for report: {}", report.getReportId());
        return entityWithMetadata;
    }

    private List<ProductPerformanceReport.CategoryAnalysis> generateCategoryAnalyses(
            List<ProductPerformanceReport.ProductMetric> metrics) {
        Map<String, List<ProductPerformanceReport.ProductMetric>> byCategory = metrics.stream()
                .collect(Collectors.groupingBy(ProductPerformanceReport.ProductMetric::getCategory));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    ProductPerformanceReport.CategoryAnalysis analysis = new ProductPerformanceReport.CategoryAnalysis();
                    analysis.setCategoryName(entry.getKey());
                    analysis.setTotalProducts(entry.getValue().size());
                    analysis.setCategoryRevenue(entry.getValue().stream()
                            .mapToDouble(ProductPerformanceReport.ProductMetric::getRevenue)
                            .sum());
                    analysis.setAverageTurnover(entry.getValue().stream()
                            .mapToDouble(ProductPerformanceReport.ProductMetric::getInventoryTurnover)
                            .average()
                            .orElse(0.0));
                    analysis.setTopProducts(entry.getValue().stream()
                            .sorted(Comparator.comparingInt(ProductPerformanceReport.ProductMetric::getSalesVolume).reversed())
                            .limit(3)
                            .map(ProductPerformanceReport.ProductMetric::getProductName)
                            .collect(Collectors.toList()));
                    return analysis;
                })
                .collect(Collectors.toList());
    }

    private List<ProductPerformanceReport.RestockingRecommendation> generateRestockingRecommendations(
            List<ProductPerformanceReport.ProductMetric> metrics) {
        return metrics.stream()
                .filter(m -> m.getCurrentStock() < 50)
                .map(m -> {
                    ProductPerformanceReport.RestockingRecommendation rec = new ProductPerformanceReport.RestockingRecommendation();
                    rec.setProductId(m.getProductId());
                    rec.setProductName(m.getProductName());
                    rec.setRecommendedQuantity(Math.max(100, (int)(m.getSalesVolume() * 0.5)));
                    rec.setUrgency(m.getCurrentStock() < 20 ? "high" : "medium");
                    rec.setReason("Low stock level: " + m.getCurrentStock() + " units");
                    return rec;
                })
                .collect(Collectors.toList());
    }
}

