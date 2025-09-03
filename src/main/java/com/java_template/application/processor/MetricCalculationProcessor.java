package com.java_template.application.processor;

import com.java_template.application.entity.performancemetric.version_1.PerformanceMetric;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class MetricCalculationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MetricCalculationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public MetricCalculationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PerformanceMetric calculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PerformanceMetric.class)
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

    private boolean isValidEntity(PerformanceMetric entity) {
        return entity != null && entity.isValid();
    }

    private PerformanceMetric processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PerformanceMetric> context) {
        PerformanceMetric entity = context.entity();

        logger.info("Calculating metric: {} for product: {}", entity.getMetricType(), entity.getProductId());

        // Get Product entity by productId
        Product product = getProductById(entity.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + entity.getProductId());
        }

        // Calculate metric value based on type
        BigDecimal calculatedValue = calculateMetricValue(entity, product);
        entity.setMetricValue(calculatedValue);

        // Detect outliers
        boolean isOutlier = detectOutlier(entity, calculatedValue);
        entity.setIsOutlier(isOutlier);

        entity.setCalculatedAt(LocalDateTime.now());

        logger.info("Metric calculation completed: {} = {} for product {}", 
                   entity.getMetricType(), calculatedValue, entity.getProductId());
        return entity;
    }

    private Product getProductById(Long productId) {
        try {
            // Convert Long to UUID for entity service call
            // In a real implementation, you'd need proper ID mapping
            UUID entityId = UUID.randomUUID(); // This is a placeholder
            EntityResponse<Product> response = entityService.getItem(entityId, Product.class);
            return response.getData();
        } catch (Exception e) {
            logger.error("Failed to get product {}: {}", productId, e.getMessage());
            return null;
        }
    }

    private BigDecimal calculateMetricValue(PerformanceMetric metric, Product product) {
        return switch (metric.getMetricType()) {
            case "SALES_VOLUME" -> calculateSalesVolume(metric, product);
            case "REVENUE" -> calculateRevenue(metric, product);
            case "INVENTORY_TURNOVER" -> calculateInventoryTurnover(metric, product);
            case "TREND_ANALYSIS" -> calculateTrendAnalysis(metric, product);
            default -> throw new IllegalArgumentException("Unknown metric type: " + metric.getMetricType());
        };
    }

    private BigDecimal calculateSalesVolume(PerformanceMetric metric, Product product) {
        // Simplified calculation - in real implementation would query sales data for period
        Integer salesVolume = product.getSalesVolume() != null ? product.getSalesVolume() : 0;
        return new BigDecimal(salesVolume);
    }

    private BigDecimal calculateRevenue(PerformanceMetric metric, Product product) {
        // Simplified calculation - in real implementation would query sales data for period
        BigDecimal revenue = product.getRevenue() != null ? product.getRevenue() : BigDecimal.ZERO;
        return revenue.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateInventoryTurnover(PerformanceMetric metric, Product product) {
        // Simplified calculation: COGS / average inventory
        BigDecimal cogs = product.getRevenue() != null ? product.getRevenue() : BigDecimal.ZERO;
        BigDecimal avgInventory = new BigDecimal(product.getStockQuantity() != null ? product.getStockQuantity() : 1);
        
        if (avgInventory.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return cogs.divide(avgInventory, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTrendAnalysis(PerformanceMetric metric, Product product) {
        // Simplified trend calculation - in real implementation would use linear regression
        // Return a percentage trend (positive = growing, negative = declining)
        return new BigDecimal("5.25").setScale(2, RoundingMode.HALF_UP); // Placeholder 5.25% growth
    }

    private boolean detectOutlier(PerformanceMetric metric, BigDecimal value) {
        // Simplified outlier detection - in real implementation would compare with historical values
        // For now, just check if value is extremely high
        BigDecimal threshold = switch (metric.getMetricType()) {
            case "SALES_VOLUME" -> new BigDecimal("1000");
            case "REVENUE" -> new BigDecimal("100000");
            case "INVENTORY_TURNOVER" -> new BigDecimal("50");
            case "TREND_ANALYSIS" -> new BigDecimal("100");
            default -> new BigDecimal("1000");
        };
        
        return value.compareTo(threshold) > 0;
    }
}
