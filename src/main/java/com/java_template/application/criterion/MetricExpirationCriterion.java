package com.java_template.application.criterion;

import com.java_template.application.entity.performancemetric.version_1.PerformanceMetric;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class MetricExpirationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MetricExpirationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking metric expiration criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(PerformanceMetric.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PerformanceMetric> context) {
        PerformanceMetric entity = context.entity();
        logger.info("Checking expiration for metric: {} of product: {}", entity.getMetricType(), entity.getProductId());

        // Check metric age based on type
        if (entity.getCalculatedAt() == null) {
            logger.warn("Metric has no calculation timestamp, cannot check expiration");
            return EvaluationOutcome.fail("Metric has no calculation timestamp", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        LocalDateTime now = LocalDateTime.now();
        long daysSinceCalculation = ChronoUnit.DAYS.between(entity.getCalculatedAt(), now);

        boolean shouldExpire = shouldMetricExpire(entity.getMetricType(), daysSinceCalculation);

        if (shouldExpire) {
            logger.info("Metric {} for product {} should expire (age: {} days)", 
                       entity.getMetricType(), entity.getProductId(), daysSinceCalculation);
            return EvaluationOutcome.success();
        }

        // Check data relevance - if underlying data has changed significantly
        if (hasUnderlyingDataChanged(entity)) {
            logger.info("Metric {} for product {} should expire due to data changes", 
                       entity.getMetricType(), entity.getProductId());
            return EvaluationOutcome.success();
        }

        // Metric is still valid
        logger.info("Metric {} for product {} is still valid (age: {} days)", 
                   entity.getMetricType(), entity.getProductId(), daysSinceCalculation);
        return EvaluationOutcome.fail("Metric is still valid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private boolean shouldMetricExpire(String metricType, long daysSinceCalculation) {
        return switch (metricType) {
            case "SALES_VOLUME", "REVENUE" -> daysSinceCalculation > 7; // Expire after 7 days
            case "INVENTORY_TURNOVER" -> daysSinceCalculation > 30; // Expire after 30 days
            case "TREND_ANALYSIS" -> daysSinceCalculation > 90; // Expire after 90 days
            default -> {
                logger.warn("Unknown metric type for expiration check: {}", metricType);
                yield daysSinceCalculation > 30; // Default to 30 days
            }
        };
    }

    private boolean hasUnderlyingDataChanged(PerformanceMetric metric) {
        // In a real implementation, this would check if:
        // 1. Product data has been updated since metric calculation
        // 2. Sales data has been updated
        // 3. Inventory data has changed
        // 4. Calculation methodology has been updated
        
        // For now, simulate occasional data changes
        // This is a simplified check - in reality would query actual data sources
        
        // Simulate that 10% of metrics have underlying data changes
        return (metric.getProductId().hashCode() + metric.getMetricType().hashCode()) % 10 == 0;
    }
}
