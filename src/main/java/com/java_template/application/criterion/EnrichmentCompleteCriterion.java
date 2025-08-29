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
public class EnrichmentCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EnrichmentCompleteCriterion(SerializerFactory serializerFactory) {
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
         Product product = context.entity();
         if (product == null) {
             logger.warn("Product entity is null in EnrichmentCompleteCriterion");
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Enrichment must populate category
         if (product.getCategory() == null || product.getCategory().isBlank()) {
             return EvaluationOutcome.fail("Product category not enriched", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Price must be present after enrichment and non-negative
         if (product.getPrice() == null) {
             return EvaluationOutcome.fail("Product price is missing after enrichment", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (product.getPrice() < 0.0) {
             return EvaluationOutcome.fail("Product price is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic sanity: productId and name should remain present
         if (product.getProductId() == null || product.getProductId().isBlank()) {
             return EvaluationOutcome.fail("Product identifier is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (product.getName() == null || product.getName().isBlank()) {
             return EvaluationOutcome.fail("Product name is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}