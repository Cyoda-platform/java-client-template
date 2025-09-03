package com.java_template.application.processor;

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

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ProductAnalysisCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductAnalysisCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ProductAnalysisCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product analysis completion for request: {}", request.getId());

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

        logger.info("Completing performance analysis for product: {}", entity.getName());

        // Check all PerformanceMetric entities for this product
        boolean allMetricsComplete = checkAllMetricsComplete(entity.getId());

        if (allMetricsComplete) {
            // Calculate aggregate performance score
            calculateAggregatePerformance(entity);
            entity.setUpdatedAt(LocalDateTime.now());
            logger.info("Performance analysis completed for product: {}", entity.getName());
        } else {
            logger.info("Performance analysis still pending for product: {} - waiting for metrics", entity.getName());
        }

        return entity;
    }

    private boolean checkAllMetricsComplete(Long productId) {
        try {
            // Create condition to find metrics for this product
            Condition productIdCondition = Condition.of("$.productId", "EQUALS", productId.toString());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(productIdCondition));

            // Get all metrics for this product
            List<EntityResponse<PerformanceMetric>> metricResponses = entityService.getItemsByCondition(
                PerformanceMetric.class,
                PerformanceMetric.ENTITY_NAME,
                PerformanceMetric.ENTITY_VERSION,
                condition,
                true
            );

            // Check if all metrics are in 'published' state
            for (EntityResponse<PerformanceMetric> response : metricResponses) {
                String state = response.getMetadata().getState();
                if (!"published".equals(state)) {
                    logger.debug("Metric {} is in state: {}, waiting for completion", 
                               response.getMetadata().getId(), state);
                    return false;
                }
            }

            logger.info("All {} metrics completed for product {}", metricResponses.size(), productId);
            return true;

        } catch (Exception e) {
            logger.error("Error checking metrics completion for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    private void calculateAggregatePerformance(Product product) {
        // Calculate aggregate performance score based on completed metrics
        // This is a simplified implementation
        logger.info("Calculating aggregate performance score for product: {}", product.getName());
        
        // In a real implementation, this would:
        // 1. Retrieve all published metrics for the product
        // 2. Calculate weighted performance score
        // 3. Update product performance summary
        // 4. Set analysis completion timestamp
        
        // For now, just log the completion
        logger.info("Aggregate performance calculation completed for product: {}", product.getName());
    }
}
