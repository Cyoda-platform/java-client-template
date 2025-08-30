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
public class StockDepletedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StockDepletedCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product product = context.entity();
         if (product == null) {
             logger.debug("Product entity is null in StockDepletedCriterion");
             return EvaluationOutcome.fail("Product entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // SKU is a required identifier for messaging
         if (product.getSku() == null || product.getSku().isBlank()) {
             return EvaluationOutcome.fail("Product SKU is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Name is important for data quality but not strictly required for business rule evaluation
         if (product.getName() == null || product.getName().isBlank()) {
             return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Quantity presence / data quality checks
         Integer qty = product.getQuantityAvailable();
         if (qty == null) {
             return EvaluationOutcome.fail("quantityAvailable is missing for product sku: " + product.getSku(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Negative quantity indicates a data quality issue (should not be negative)
         if (qty < 0) {
             return EvaluationOutcome.fail("quantityAvailable is negative for product sku: " + product.getSku(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: stock depleted when exactly zero
         if (qty == 0) {
             return EvaluationOutcome.fail("Stock depleted for product sku: " + product.getSku(),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // quantity > 0 => success
         return EvaluationOutcome.success();
    }
}