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
public class StockLowCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Threshold under which stock is considered "low". Chosen as a conservative default.
    private static final int LOW_STOCK_THRESHOLD = 5;

    public StockLowCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive) according to critical requirements.
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product product = context.entity();

         if (product == null) {
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic identity validation
         if (product.getSku() == null || product.getSku().isBlank()) {
             return EvaluationOutcome.fail("Product SKU is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Quantity presence & sanity
         Integer qty = product.getQuantityAvailable();
         if (qty == null) {
             return EvaluationOutcome.fail("quantityAvailable is missing for product: " + product.getSku(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (qty < 0) {
             return EvaluationOutcome.fail("quantityAvailable is negative for product: " + product.getSku(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: low stock
         if (qty <= LOW_STOCK_THRESHOLD) {
             String namePart = product.getName() != null ? (" (" + product.getName() + ")") : "";
             return EvaluationOutcome.fail(
                 "Stock low for sku " + product.getSku() + namePart + ": " + qty + " unit(s) available",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}