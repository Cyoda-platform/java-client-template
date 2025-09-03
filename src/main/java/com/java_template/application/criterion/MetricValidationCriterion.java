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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class MetricValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MetricValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking metric validation criteria for request: {}", request.getId());
        
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
        logger.info("Validating metric: {} for product: {}", entity.getMetricType(), entity.getProductId());

        // Check metric value validity
        if (entity.getMetricValue() == null) {
            return EvaluationOutcome.fail("Metric value cannot be null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if metric value is negative (except for trend analysis)
        if (!"TREND_ANALYSIS".equals(entity.getMetricType()) && entity.getMetricValue().compareTo(BigDecimal.ZERO) < 0) {
            return EvaluationOutcome.fail("Metric value cannot be negative for type: " + entity.getMetricType(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if metric value is within expected range for metric type
        if (!isValueWithinExpectedRange(entity)) {
            return EvaluationOutcome.fail("Metric value is outside expected range for type: " + entity.getMetricType(), 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check calculation period validity
        if (entity.getPeriodStart() == null || entity.getPeriodEnd() == null) {
            return EvaluationOutcome.fail("Period start and end dates are required", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getPeriodStart().isAfter(entity.getPeriodEnd())) {
            return EvaluationOutcome.fail("Period start date must be before or equal to end date", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getPeriodEnd().isAfter(LocalDate.now())) {
            return EvaluationOutcome.fail("Period end date cannot be in the future", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check calculation period matches metric type requirements
        if (!isPeriodValidForMetricType(entity)) {
            return EvaluationOutcome.fail("Calculation period does not match metric type requirements", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check data consistency - verify calculation timestamp is recent
        if (entity.getCalculatedAt() != null) {
            long hoursSinceCalculation = ChronoUnit.HOURS.between(entity.getCalculatedAt(), LocalDateTime.now());
            if (hoursSinceCalculation > 24) {
                logger.warn("Metric calculation timestamp is {} hours old for product {}", 
                           hoursSinceCalculation, entity.getProductId());
            }
        }

        // All validations passed
        logger.info("Metric validation passed for: {} of product {}", entity.getMetricType(), entity.getProductId());
        return EvaluationOutcome.success();
    }

    private boolean isValueWithinExpectedRange(PerformanceMetric metric) {
        BigDecimal value = metric.getMetricValue();
        String metricType = metric.getMetricType();

        return switch (metricType) {
            case "SALES_VOLUME" -> value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(new BigDecimal("10000")) <= 0;
            case "REVENUE" -> value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(new BigDecimal("1000000")) <= 0;
            case "INVENTORY_TURNOVER" -> value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(new BigDecimal("100")) <= 0;
            case "TREND_ANALYSIS" -> value.compareTo(new BigDecimal("-100")) >= 0 && value.compareTo(new BigDecimal("100")) <= 0;
            default -> {
                logger.warn("Unknown metric type for range validation: {}", metricType);
                yield true; // Allow unknown types
            }
        };
    }

    private boolean isPeriodValidForMetricType(PerformanceMetric metric) {
        String calculationPeriod = metric.getCalculationPeriod();
        String metricType = metric.getMetricType();

        // Check if calculation period is appropriate for metric type
        return switch (metricType) {
            case "SALES_VOLUME", "REVENUE" -> "DAILY".equals(calculationPeriod) || "WEEKLY".equals(calculationPeriod);
            case "INVENTORY_TURNOVER" -> "WEEKLY".equals(calculationPeriod) || "MONTHLY".equals(calculationPeriod);
            case "TREND_ANALYSIS" -> "MONTHLY".equals(calculationPeriod);
            default -> {
                logger.warn("Unknown metric type for period validation: {}", metricType);
                yield true; // Allow unknown types
            }
        };
    }
}
