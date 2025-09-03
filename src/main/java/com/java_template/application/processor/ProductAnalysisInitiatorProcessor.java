package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Component
public class ProductAnalysisInitiatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductAnalysisInitiatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ProductAnalysisInitiatorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product analysis initiation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
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

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();

        logger.info("Initiating performance analysis for product: {}", entity.getName());

        // Create PerformanceMetric entities for product
        createPerformanceMetrics(entity);

        entity.setUpdatedAt(LocalDateTime.now());

        logger.info("Performance analysis initiated for product: {}", entity.getName());
        return entity;
    }

    private void createPerformanceMetrics(Product product) {
        LocalDate now = LocalDate.now();
        
        // Create SALES_VOLUME metric for current week
        createMetric(product.getId(), "SALES_VOLUME", "WEEKLY", 
                    now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), 
                    now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)));

        // Create REVENUE metric for current week
        createMetric(product.getId(), "REVENUE", "WEEKLY", 
                    now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), 
                    now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)));

        // Create INVENTORY_TURNOVER metric for current month
        createMetric(product.getId(), "INVENTORY_TURNOVER", "MONTHLY", 
                    now.with(TemporalAdjusters.firstDayOfMonth()), 
                    now.with(TemporalAdjusters.lastDayOfMonth()));

        // Create TREND_ANALYSIS metric for last 3 months
        createMetric(product.getId(), "TREND_ANALYSIS", "MONTHLY", 
                    now.minusMonths(3), now);
    }

    private void createMetric(Long productId, String metricType, String calculationPeriod, 
                             LocalDate periodStart, LocalDate periodEnd) {
        PerformanceMetric metric = new PerformanceMetric();
        metric.setProductId(productId);
        metric.setMetricType(metricType);
        metric.setCalculationPeriod(calculationPeriod);
        metric.setPeriodStart(periodStart);
        metric.setPeriodEnd(periodEnd);
        metric.setIsOutlier(false);

        try {
            entityService.save(metric);
            logger.info("Created {} metric for product {}", metricType, productId);
        } catch (Exception e) {
            logger.error("Failed to create {} metric for product {}: {}", metricType, productId, e.getMessage());
        }
    }
}
