package com.java_template.application.criterion;

import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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

@Component
public class AggregationCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AggregationCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(SalesRecord.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SalesRecord> context) {
         SalesRecord entity = context.entity();

         // Validate required identifiers and timestamp
         if (entity.getProductId() == null || entity.getProductId().isBlank()) {
             logger.debug("AggregationCompleteCriterion: missing productId for record {}", entity.getRecordId());
             return EvaluationOutcome.fail("productId is required for aggregation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDateSold() == null || entity.getDateSold().isBlank()) {
             logger.debug("AggregationCompleteCriterion: missing dateSold for record {}", entity.getRecordId());
             return EvaluationOutcome.fail("dateSold is required for aggregation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate numeric measures
         if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
             logger.debug("AggregationCompleteCriterion: invalid quantity ({}) for record {}", entity.getQuantity(), entity.getRecordId());
             return EvaluationOutcome.fail("quantity must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRevenue() == null) {
             logger.debug("AggregationCompleteCriterion: missing revenue for record {}", entity.getRecordId());
             return EvaluationOutcome.fail("revenue is required for aggregation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRevenue() < 0.0) {
             logger.debug("AggregationCompleteCriterion: negative revenue ({}) for record {}", entity.getRevenue(), entity.getRecordId());
             return EvaluationOutcome.fail("revenue must not be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule checks on aggregated measures
         // Zero revenue while positive quantity likely indicates data quality or special-case; flag as business rule failure
         if (entity.getRevenue().doubleValue() == 0.0 && entity.getQuantity().intValue() > 0) {
             logger.debug("AggregationCompleteCriterion: zero revenue for positive quantity for record {}", entity.getRecordId());
             return EvaluationOutcome.fail("zero revenue for sold quantity indicates data issue", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}