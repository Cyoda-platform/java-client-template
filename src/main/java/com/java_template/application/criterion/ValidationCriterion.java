package com.java_template.application.criterion;

import com.java_template.application.entity.product.version_1.Product;
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
public class ValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Product.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product entity = context.entity();
         if (entity == null) {
             logger.warn("Product entity is null in ValidationCriterion");
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
         if (entity.getSku() == null || entity.getSku().isBlank()) {
             return EvaluationOutcome.fail("sku is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric fields presence
         if (entity.getPrice() == null) {
             return EvaluationOutcome.fail("price is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAvailableQuantity() == null) {
             return EvaluationOutcome.fail("availableQuantity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business / data quality rules
         if (entity.getPrice() < 0.0) {
             return EvaluationOutcome.fail("price must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getAvailableQuantity() < 0) {
             return EvaluationOutcome.fail("availableQuantity must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Note: active flag is set by activation processor based on availableQuantity,
         // so do not fail if active is null here. This criterion validates core fields only.

         return EvaluationOutcome.success();
    }
}