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
import java.util.*;
import java.util.stream.Collectors;

/**
 * AnalysisProcessor
 * Analyzes product metrics and computes KPIs
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

        // Calculate KPIs
        report.setTotalSalesVolume((double) metrics.stream()
                .mapToInt(ProductPerformanceReport.ProductMetric::getSalesVolume)
                .sum());

        report.setTotalRevenue(metrics.stream()
                .mapToDouble(ProductPerformanceReport.ProductMetric::getRevenue)
                .sum());

        report.setAverageInventoryTurnover(metrics.stream()
                .mapToDouble(ProductPerformanceReport.ProductMetric::getInventoryTurnover)
                .average()
                .orElse(0.0));

        // Identify top selling products
        report.setTopSellingProducts(metrics.stream()
                .sorted(Comparator.comparingInt(ProductPerformanceReport.ProductMetric::getSalesVolume).reversed())
                .limit(5)
                .map(ProductPerformanceReport.ProductMetric::getProductName)
                .collect(Collectors.toList()));

        // Generate category analyses
        report.setCategoryAnalyses(generateCategoryAnalyses(metrics));

        // Generate restocking recommendations
        report.setRestockingRecommendations(generateRestockingRecommendations(metrics));

        report.setUpdatedAt(LocalDateTime.now());

        logger.info("Analysis completed for report: {}", report.getReportId());
        return entityWithMetadata;
    }

    private List<ProductPerformanceReport.CategoryAnalysis> generateCategoryAnalyses(
            List<ProductPerformanceReport.ProductMetric> metrics) {
        return metrics.stream()
                .collect(Collectors.groupingBy(ProductPerformanceReport.ProductMetric::getCategory))
                .entrySet().stream()
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
                .filter(m -> m.getCurrentStock() < 20 || m.getInventoryTurnover() > 4.0)
                .map(m -> {
                    ProductPerformanceReport.RestockingRecommendation rec = new ProductPerformanceReport.RestockingRecommendation();
                    rec.setProductId(m.getProductId());
                    rec.setProductName(m.getProductName());
                    rec.setRecommendedQuantity(Math.max(20, (int)(m.getSalesVolume() * 0.5)));
                    rec.setUrgency(m.getCurrentStock() < 10 ? "high" : "medium");
                    rec.setReason(m.getCurrentStock() < 10 ? "Critical stock level" : "Low inventory with high turnover");
                    return rec;
                })
                .collect(Collectors.toList());
    }
}

