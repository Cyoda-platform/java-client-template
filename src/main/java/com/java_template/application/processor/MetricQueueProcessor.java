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

@Component
public class MetricQueueProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MetricQueueProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public MetricQueueProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PerformanceMetric queue for request: {}", request.getId());

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

        logger.info("Queueing metric calculation for product: {} type: {}", entity.getProductId(), entity.getMetricType());

        // Validate metric parameters
        if (entity.getProductId() == null || entity.getProductId() <= 0) {
            throw new IllegalArgumentException("Product ID must be valid");
        }
        if (entity.getMetricType() == null || entity.getMetricType().trim().isEmpty()) {
            throw new IllegalArgumentException("Metric type is required");
        }
        if (entity.getCalculationPeriod() == null || entity.getCalculationPeriod().trim().isEmpty()) {
            throw new IllegalArgumentException("Calculation period is required");
        }

        // Set calculation priority based on metric type
        String priority = getCalculationPriority(entity.getMetricType());
        logger.info("Set priority {} for metric type {}", priority, entity.getMetricType());

        // Initialize calculation state
        entity.setCalculatedAt(null); // Not calculated yet
        if (entity.getIsOutlier() == null) {
            entity.setIsOutlier(false); // Default
        }

        logger.info("Metric queued for calculation: {} for product {}", entity.getMetricType(), entity.getProductId());
        return entity;
    }

    private String getCalculationPriority(String metricType) {
        return switch (metricType) {
            case "SALES_VOLUME", "REVENUE" -> "HIGH";
            case "INVENTORY_TURNOVER" -> "MEDIUM";
            case "TREND_ANALYSIS" -> "LOW";
            default -> "MEDIUM";
        };
    }
}
