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
import java.util.ArrayList;
import java.util.List;

/**
 * DataExtractionProcessor
 * Fetches product data from Pet Store API and populates initial metrics
 */
@Component
public class DataExtractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DataExtractionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing data extraction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ProductPerformanceReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processDataExtraction)
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

    private EntityWithMetadata<ProductPerformanceReport> processDataExtraction(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ProductPerformanceReport> context) {

        EntityWithMetadata<ProductPerformanceReport> entityWithMetadata = context.entityResponse();
        ProductPerformanceReport report = entityWithMetadata.entity();

        logger.debug("Extracting data for report: {}", report.getReportId());

        // Simulate fetching from Pet Store API
        List<ProductPerformanceReport.ProductMetric> metrics = fetchProductMetricsFromAPI();
        report.setProductMetrics(metrics);

        // Update timestamp
        report.setUpdatedAt(LocalDateTime.now());

        logger.info("Data extraction completed for report: {}", report.getReportId());
        return entityWithMetadata;
    }

    private List<ProductPerformanceReport.ProductMetric> fetchProductMetricsFromAPI() {
        List<ProductPerformanceReport.ProductMetric> metrics = new ArrayList<>();

        // Simulate API call to Pet Store API
        metrics.add(createMetric("PROD-001", "Dog Food Premium", "Food", 450, 4500.0, 120, 3.75, "high"));
        metrics.add(createMetric("PROD-002", "Cat Toys Bundle", "Toys", 180, 1800.0, 45, 4.0, "high"));
        metrics.add(createMetric("PROD-003", "Bird Cage Large", "Cages", 25, 2500.0, 8, 3.125, "low"));
        metrics.add(createMetric("PROD-004", "Fish Tank Filter", "Accessories", 320, 3200.0, 60, 5.33, "high"));
        metrics.add(createMetric("PROD-005", "Rabbit Hutch", "Housing", 15, 1500.0, 3, 5.0, "low"));

        logger.info("Fetched {} product metrics from API", metrics.size());
        return metrics;
    }

    private ProductPerformanceReport.ProductMetric createMetric(
            String productId, String productName, String category, Integer salesVolume,
            Double revenue, Integer currentStock, Double turnover, String status) {
        ProductPerformanceReport.ProductMetric metric = new ProductPerformanceReport.ProductMetric();
        metric.setProductId(productId);
        metric.setProductName(productName);
        metric.setCategory(category);
        metric.setSalesVolume(salesVolume);
        metric.setRevenue(revenue);
        metric.setCurrentStock(currentStock);
        metric.setInventoryTurnover(turnover);
        metric.setPerformanceStatus(status);
        return metric;
    }
}

