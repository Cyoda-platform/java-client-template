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
public class ProductReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductReadyCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product entity = context.entity();
         if (entity == null) {
             logger.warn("Product entity is null in ProductReadyCriterion");
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic required fields validation
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Product id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSku() == null || entity.getSku().isBlank()) {
             return EvaluationOutcome.fail("Product sku is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCurrency() == null || entity.getCurrency().isBlank()) {
             return EvaluationOutcome.fail("Product currency is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric validations
         if (entity.getPrice() == null) {
             return EvaluationOutcome.fail("Product price is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrice() < 0.0) {
             return EvaluationOutcome.fail("Product price must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStock() == null) {
             return EvaluationOutcome.fail("Product stock is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStock() < 0) {
             return EvaluationOutcome.fail("Product stock must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Availability flag presence
         if (entity.getAvailable() == null) {
             return EvaluationOutcome.fail("Product availability flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules: product must be available and have stock to be considered ready
         if (!entity.getAvailable()) {
             return EvaluationOutcome.fail("Product is not marked as available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (entity.getStock() != null && entity.getStock() <= 0) {
             return EvaluationOutcome.fail("Product is out of stock", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}