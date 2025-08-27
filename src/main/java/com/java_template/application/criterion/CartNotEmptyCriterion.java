package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
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

import java.util.List;

@Component
public class CartNotEmptyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartNotEmptyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
         Cart entity = context.entity();

         // Basic presence checks: lines must exist
         List<Cart.Line> lines = entity.getLines();
         if (lines == null || lines.isEmpty()) {
             return EvaluationOutcome.fail("Cart has no lines", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // totalItems must be present and > 0
         Integer totalItems = entity.getTotalItems();
         if (totalItems == null || totalItems <= 0) {
             return EvaluationOutcome.fail("Cart.totalItems must be > 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // grandTotal must be present and > 0
         Double grandTotal = entity.getGrandTotal();
         if (grandTotal == null || grandTotal <= 0d) {
             return EvaluationOutcome.fail("Cart.grandTotal must be > 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate each line and verify sum of quantities equals totalItems
         int sumQty = 0;
         for (int i = 0; i < lines.size(); i++) {
             Cart.Line line = lines.get(i);
             if (line == null) {
                 return EvaluationOutcome.fail("Cart contains null line at index " + i, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // sku
             if (line.getSku() == null || line.getSku().isBlank()) {
                 return EvaluationOutcome.fail("Cart line sku is required at index " + i, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // name
             if (line.getName() == null || line.getName().isBlank()) {
                 return EvaluationOutcome.fail("Cart line name is required at index " + i, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // price
             if (line.getPrice() == null || line.getPrice() < 0d) {
                 return EvaluationOutcome.fail("Cart line price is invalid at index " + i, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // qty
             if (line.getQty() == null || line.getQty() <= 0) {
                 return EvaluationOutcome.fail("Cart line qty must be > 0 at index " + i, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             sumQty += line.getQty();
         }

         if (sumQty != totalItems.intValue()) {
             return EvaluationOutcome.fail("Cart.totalItems does not match sum of line quantities", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed: cart is considered not empty and consistent
         return EvaluationOutcome.success();
    }
}