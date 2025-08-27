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
public class PaymentFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentFailedCriterion(SerializerFactory serializerFactory) {
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
        return "PaymentFailedCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
         Cart entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Cart must be in CHECKING_OUT state to be considered for payment-failed handling
         String status = entity.getStatus();
         if (status == null || !status.equals("CHECKING_OUT")) {
             return EvaluationOutcome.fail("Cart must be in CHECKING_OUT state to process payment failure", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Cart must have lines
         List<Cart.Line> lines = entity.getLines();
         if (lines == null || lines.isEmpty()) {
             return EvaluationOutcome.fail("Cart must not be empty when handling payment failure", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate line quantities and accumulate
         int sumQty = 0;
         for (Cart.Line line : lines) {
             if (line == null) {
                 return EvaluationOutcome.fail("Cart contains null line item", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             Integer qty = line.getQty();
             if (qty == null || qty <= 0) {
                 return EvaluationOutcome.fail("Cart line with invalid quantity detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // price/sku/name checks not strictly required here but ensure sku exists
             if (line.getSku() == null || line.getSku().isBlank()) {
                 return EvaluationOutcome.fail("Cart line missing sku", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             sumQty += qty;
         }

         // totalItems must match sum of line quantities
         Integer totalItems = entity.getTotalItems();
         if (totalItems == null || totalItems.intValue() != sumQty) {
             return EvaluationOutcome.fail("totalItems does not match sum of line quantities", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // reservationBatchId is expected for a checking-out cart so reservations can be released on payment failure
         String reservationBatchId = entity.getReservationBatchId();
         if (reservationBatchId == null || reservationBatchId.isBlank()) {
             return EvaluationOutcome.fail("Missing reservationBatchId for cart in CHECKING_OUT", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // grandTotal must be present and non-negative
         Double grandTotal = entity.getGrandTotal();
         if (grandTotal == null || grandTotal < 0d) {
             return EvaluationOutcome.fail("Invalid grandTotal on cart", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}