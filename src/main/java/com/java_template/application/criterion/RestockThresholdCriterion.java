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
public class RestockThresholdCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RestockThresholdCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventorySnapshot> context) {
         InventorySnapshot entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("InventorySnapshot entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer stockLevel = entity.getStockLevel();
         Integer restockThreshold = entity.getRestockThreshold();

         if (stockLevel == null) {
             return EvaluationOutcome.fail("stockLevel is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (restockThreshold == null) {
             return EvaluationOutcome.fail("restockThreshold is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (stockLevel < 0) {
             return EvaluationOutcome.fail("stockLevel must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (restockThreshold < 0) {
             return EvaluationOutcome.fail("restockThreshold must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: if current stock is strictly less than threshold, it needs restock
         if (stockLevel < restockThreshold) {
             // success indicates the entity meets the criterion for "needs restock"
             return EvaluationOutcome.success();
         } else {
             // when stock is sufficient, the criterion for restock is not met
             return EvaluationOutcome.fail("Stock level is sufficient (no restock needed)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
    }
}