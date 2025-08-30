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

@Component
public class CheckoutSignalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckoutSignalCriterion(SerializerFactory serializerFactory) {
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

         if (entity == null) {
             return EvaluationOutcome.fail("Cart entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: cart must be in CHECKING_OUT to accept a checkout signal
         if (entity.getStatus() == null || !entity.getStatus().equals("CHECKING_OUT")) {
             return EvaluationOutcome.fail("Cart must be in CHECKING_OUT to apply checkout signal", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic presence checks
         if (entity.getLines() == null || entity.getLines().isEmpty()) {
             return EvaluationOutcome.fail("Cart has no lines", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Guest contact must be present and valid for checkout
         if (entity.getGuestContact() == null || !entity.getGuestContact().isValid()) {
             return EvaluationOutcome.fail("Guest contact missing or invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // grandTotal must be present and non-negative
         if (entity.getGrandTotal() == null) {
             return EvaluationOutcome.fail("grandTotal is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGrandTotal() < 0) {
             return EvaluationOutcome.fail("grandTotal must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // totalItems must be present and non-negative
         if (entity.getTotalItems() == null) {
             return EvaluationOutcome.fail("totalItems is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTotalItems() < 0) {
             return EvaluationOutcome.fail("totalItems must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Recompute totals from lines and compare
         double computedGrand = 0.0;
         int computedItems = 0;
         for (Cart.Line line : entity.getLines()) {
             if (line == null) {
                 return EvaluationOutcome.fail("Cart contains a null line", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (line.getSku() == null || line.getSku().isBlank()) {
                 return EvaluationOutcome.fail("Cart contains a line with missing sku", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (line.getPrice() == null) {
                 return EvaluationOutcome.fail("Cart contains a line with missing price", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (line.getQty() == null || line.getQty() <= 0) {
                 return EvaluationOutcome.fail("Cart contains a line with invalid qty", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             computedGrand += line.getPrice() * line.getQty();
             computedItems += line.getQty();
         }

         // Allow small floating point tolerance
         double epsilon = 0.01d;
         if (Math.abs(computedGrand - entity.getGrandTotal()) > epsilon) {
             return EvaluationOutcome.fail("grandTotal does not match sum of line totals", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (!entity.getTotalItems().equals(computedItems)) {
             return EvaluationOutcome.fail("totalItems does not match sum of line quantities", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}