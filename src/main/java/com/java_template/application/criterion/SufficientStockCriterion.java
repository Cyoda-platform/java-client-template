package com.java_template.application.criterion;

import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
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
public class SufficientStockCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SufficientStockCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(InventorySnapshot.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventorySnapshot> context) {
         InventorySnapshot entity = context.entity();

         if (entity == null) {
             logger.debug("InventorySnapshot entity is null in context");
             return EvaluationOutcome.fail("InventorySnapshot entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate presence of identifying and context fields
         if (entity.getSnapshotId() == null || entity.getSnapshotId().isBlank()) {
             return EvaluationOutcome.fail("snapshotId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getProductId() == null || entity.getProductId().isBlank()) {
             return EvaluationOutcome.fail("productId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSnapshotAt() == null || entity.getSnapshotAt().isBlank()) {
             return EvaluationOutcome.fail("snapshotAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks for numeric fields
         Integer stockLevel = entity.getStockLevel();
         Integer restockThreshold = entity.getRestockThreshold();

         if (stockLevel == null) {
             return EvaluationOutcome.fail("stockLevel is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (restockThreshold == null) {
             return EvaluationOutcome.fail("restockThreshold is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (stockLevel < 0) {
             return EvaluationOutcome.fail("stockLevel must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (restockThreshold < 0) {
             return EvaluationOutcome.fail("restockThreshold must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: sufficient stock means current stockLevel is greater than or equal to restockThreshold.
         if (stockLevel < restockThreshold) {
             String msg = String.format("Stock insufficient for product %s: stockLevel=%d below restockThreshold=%d",
                 entity.getProductId(), stockLevel, restockThreshold);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}