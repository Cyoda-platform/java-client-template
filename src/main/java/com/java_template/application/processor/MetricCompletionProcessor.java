package com.java_template.application.processor;

import com.java_template.application.entity.performancemetric.version_1.PerformanceMetric;
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

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MetricCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MetricCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public MetricCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PerformanceMetric completion for request: {}", request.getId());

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

        logger.info("Completing metric calculation: {} for product: {}", entity.getMetricType(), entity.getProductId());

        // Validate calculated metric value
        if (entity.getMetricValue() == null) {
            throw new IllegalStateException("Metric value cannot be null");
        }

        // Validate metric value is within reasonable bounds
        validateMetricBounds(entity);

        // Round metric value to appropriate precision
        BigDecimal roundedValue = roundMetricValue(entity);
        entity.setMetricValue(roundedValue);

        logger.info("Metric calculation finalized: {} = {} for product {}", 
                   entity.getMetricType(), roundedValue, entity.getProductId());
        return entity;
    }

    private void validateMetricBounds(PerformanceMetric metric) {
        BigDecimal value = metric.getMetricValue();
        String metricType = metric.getMetricType();

        // Check if value is within reasonable bounds
        switch (metricType) {
            case "SALES_VOLUME":
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("10000")) > 0) {
                    logger.warn("Sales volume {} is outside expected range for product {}", 
                               value, metric.getProductId());
                }
                break;
            case "REVENUE":
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("1000000")) > 0) {
                    logger.warn("Revenue {} is outside expected range for product {}", 
                               value, metric.getProductId());
                }
                break;
            case "INVENTORY_TURNOVER":
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                    logger.warn("Inventory turnover {} is outside expected range for product {}", 
                               value, metric.getProductId());
                }
                break;
            case "TREND_ANALYSIS":
                if (value.compareTo(new BigDecimal("-100")) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                    logger.warn("Trend analysis {} is outside expected range for product {}", 
                               value, metric.getProductId());
                }
                break;
            default:
                logger.warn("Unknown metric type for validation: {}", metricType);
        }
    }

    private BigDecimal roundMetricValue(PerformanceMetric metric) {
        BigDecimal value = metric.getMetricValue();
        String metricType = metric.getMetricType();

        return switch (metricType) {
            case "SALES_VOLUME" -> value.setScale(0, RoundingMode.HALF_UP); // Round to integer
            case "REVENUE" -> value.setScale(2, RoundingMode.HALF_UP); // Round to 2 decimal places
            case "INVENTORY_TURNOVER" -> value.setScale(3, RoundingMode.HALF_UP); // Round to 3 decimal places
            case "TREND_ANALYSIS" -> value.setScale(2, RoundingMode.HALF_UP); // Round to 2 decimal places
            default -> value.setScale(2, RoundingMode.HALF_UP); // Default to 2 decimal places
        };
    }
}
