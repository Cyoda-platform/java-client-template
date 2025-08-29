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
public class ProductValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Product.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product entity = context.entity();
         if (entity == null) {
             logger.debug("Product entity is null in evaluation context");
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required: productId
         if (entity.getProductId() == null || entity.getProductId().isBlank()) {
             return EvaluationOutcome.fail("productId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: name
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Category is important for analysis - treat missing category as validation failure
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Price must be present and non-negative
         if (entity.getPrice() == null) {
             return EvaluationOutcome.fail("price is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrice() < 0.0) {
             return EvaluationOutcome.fail("price must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: extremely low (zero) price is suspicious -> flag as business rule failure
         if (entity.getPrice() == 0.0) {
             return EvaluationOutcome.fail("price is zero - suspicious for a persisted product", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality check: metadata if provided should not be blank
         if (entity.getMetadata() != null && entity.getMetadata().isBlank()) {
             return EvaluationOutcome.fail("metadata is blank when provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}