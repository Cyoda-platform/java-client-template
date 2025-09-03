package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.performancemetric.version_1.PerformanceMetric;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReportGenerationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ReportGenerationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report generation for request: {}", request.getId());

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

        logger.info("Generating report: {}", entity.getReportName());

        try {
            // Query all Products in 'analyzed' state for report period
            List<Product> analyzedProducts = getAnalyzedProducts();
            
            // Query all PerformanceMetrics in 'published' state for report period
            List<PerformanceMetric> publishedMetrics = getPublishedMetrics();

            // Analyze data and generate report content
            analyzeDataAndGenerateReport(entity, analyzedProducts, publishedMetrics);

            // Generate report file (simulated)
            generateReportFile(entity);

            logger.info("Report generation completed: {}", entity.getReportName());
        } catch (Exception e) {
            logger.error("Failed to generate report {}: {}", entity.getReportName(), e.getMessage());
            throw new RuntimeException("Report generation failed", e);
        }

        return entity;
    }

    private List<Product> getAnalyzedProducts() {
        try {
            // Create condition to find products in 'analyzed' state
            Condition stateCondition = Condition.lifecycle("state", "EQUALS", "analyzed");
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(stateCondition));

            List<EntityResponse<Product>> productResponses = entityService.getItemsByCondition(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            );

            return productResponses.stream()
                    .map(EntityResponse::getData)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get analyzed products: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<PerformanceMetric> getPublishedMetrics() {
        try {
            // Create condition to find metrics in 'published' state
            Condition stateCondition = Condition.lifecycle("state", "EQUALS", "published");
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(stateCondition));

            List<EntityResponse<PerformanceMetric>> metricResponses = entityService.getItemsByCondition(
                PerformanceMetric.class,
                PerformanceMetric.ENTITY_NAME,
                PerformanceMetric.ENTITY_VERSION,
                condition,
                true
            );

            return metricResponses.stream()
                    .map(EntityResponse::getData)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get published metrics: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void analyzeDataAndGenerateReport(Report report, List<Product> products, List<PerformanceMetric> metrics) {
        // Calculate total products analyzed
        report.setTotalProducts(products.size());

        // Identify top 5 performing products by revenue
        List<String> topPerforming = products.stream()
                .filter(p -> p.getRevenue() != null)
                .sorted((p1, p2) -> p2.getRevenue().compareTo(p1.getRevenue()))
                .limit(5)
                .map(Product::getName)
                .collect(Collectors.toList());
        report.setTopPerformingProducts(topPerforming);

        // Identify bottom 5 performing products by sales volume
        List<String> underperforming = products.stream()
                .filter(p -> p.getSalesVolume() != null)
                .sorted((p1, p2) -> p1.getSalesVolume().compareTo(p2.getSalesVolume()))
                .limit(5)
                .map(Product::getName)
                .collect(Collectors.toList());
        report.setUnderperformingProducts(underperforming);

        // Calculate key insights
        List<String> insights = generateKeyInsights(products, metrics);
        report.setKeyInsights(insights);
    }

    private List<String> generateKeyInsights(List<Product> products, List<PerformanceMetric> metrics) {
        List<String> insights = new ArrayList<>();

        // Total revenue for period
        BigDecimal totalRevenue = products.stream()
                .filter(p -> p.getRevenue() != null)
                .map(Product::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        insights.add("Total revenue for period: $" + totalRevenue);

        // Products needing restocking
        long lowStockCount = products.stream()
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() < 10)
                .count();
        insights.add("Products needing restocking: " + lowStockCount);

        // Trending products (simplified)
        long trendingCount = metrics.stream()
                .filter(m -> "TREND_ANALYSIS".equals(m.getMetricType()))
                .filter(m -> m.getMetricValue() != null && m.getMetricValue().compareTo(BigDecimal.ZERO) > 0)
                .count();
        insights.add("Trending products with positive growth: " + trendingCount);

        return insights;
    }

    private void generateReportFile(Report report) {
        // Simulate report file generation
        String filePath = report.getFilePath() + ".pdf";
        report.setFilePath(filePath);
        
        // Create summary for email body
        StringBuilder summary = new StringBuilder();
        summary.append("Weekly Performance Report Summary\n\n");
        summary.append("Report Period: ").append(report.getReportPeriodStart())
               .append(" to ").append(report.getReportPeriodEnd()).append("\n");
        summary.append("Total Products Analyzed: ").append(report.getTotalProducts()).append("\n\n");
        
        if (!report.getKeyInsights().isEmpty()) {
            summary.append("Key Insights:\n");
            for (String insight : report.getKeyInsights()) {
                summary.append("- ").append(insight).append("\n");
            }
        }
        
        report.setSummary(summary.toString());
        
        logger.info("Report file generated: {}", filePath);
    }
}
