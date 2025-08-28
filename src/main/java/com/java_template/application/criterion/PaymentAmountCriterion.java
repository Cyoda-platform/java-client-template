package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.payment.version_1.Payment;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PaymentAmountCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentAmountCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Payment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
         Payment payment = context.entity();

         // Basic payment presence checks (use existing getters)
         if (payment == null) {
             return EvaluationOutcome.fail("Payment entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (payment.getCartId() == null || payment.getCartId().isBlank()) {
             return EvaluationOutcome.fail("Payment.cartId is required to verify amount", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getAmount() == null) {
             return EvaluationOutcome.fail("Payment.amount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Attempt to load the referenced Cart. The evaluation context is expected to provide a fetch mechanism.
         Cart cart;
         try {
             // The context is expected to be able to fetch related entities by class and id.
             cart = context.fetchEntity(Cart.class, payment.getCartId());
         } catch (NoSuchMethodError nsme) {
             // Defensive fallback message if fetchEntity isn't available in the runtime context API
             logger.warn("fetchEntity not available on context - cannot validate payment amount against cart", nsme);
             return EvaluationOutcome.fail("Cannot validate payment amount: unable to load cart", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         } catch (Exception e) {
             logger.warn("Error while loading cart for payment validation: {}", e.getMessage(), e);
             return EvaluationOutcome.fail("Error loading cart for payment validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (cart == null) {
             return EvaluationOutcome.fail("Referenced cart not found for payment.cartId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<CartItem> items = cart.getItems();
         if (items == null || items.isEmpty()) {
             return EvaluationOutcome.fail("Cart has no items to validate payment amount against", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Compute expected total from cart items: sum(priceSnapshot * qty)
         BigDecimal expectedTotal = BigDecimal.ZERO;
         for (CartItem item : items) {
             if (item == null) {
                 return EvaluationOutcome.fail("Cart contains a null item", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             Double priceSnapshot = item.getPriceSnapshot();
             Integer qty = item.getQty();
             if (priceSnapshot == null || qty == null) {
                 return EvaluationOutcome.fail("Cart item missing priceSnapshot or qty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (priceSnapshot < 0.0 || qty < 0) {
                 return EvaluationOutcome.fail("Cart item has invalid priceSnapshot or qty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             BigDecimal line = BigDecimal.valueOf(priceSnapshot).multiply(BigDecimal.valueOf(qty));
             expectedTotal = expectedTotal.add(line);
         }

         BigDecimal paymentAmount = BigDecimal.valueOf(payment.getAmount());
         // Normalize scale to cents for comparison
         expectedTotal = expectedTotal.setScale(2, RoundingMode.HALF_UP);
         paymentAmount = paymentAmount.setScale(2, RoundingMode.HALF_UP);

         if (expectedTotal.compareTo(paymentAmount) != 0) {
             String msg = String.format("Payment.amount (%.2f) does not match cart total (%.2f)", paymentAmount.doubleValue(), expectedTotal.doubleValue());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}