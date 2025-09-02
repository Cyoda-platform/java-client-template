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
public class CartValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCart)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCart(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();

        if (cart == null) {
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Cart must have at least one item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        double calculatedTotal = 0.0;
        int calculatedItems = 0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return EvaluationOutcome.fail("Cart line must have valid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQty() == null || line.getQty() <= 0) {
                return EvaluationOutcome.fail("Cart line quantity must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getPrice() == null || line.getPrice() < 0) {
                return EvaluationOutcome.fail("Cart line price cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            calculatedTotal += line.getPrice() * line.getQty();
            calculatedItems += line.getQty();
        }

        if (cart.getGrandTotal() == null || Math.abs(cart.getGrandTotal() - calculatedTotal) > 0.01) {
            return EvaluationOutcome.fail("Cart grand total is incorrect", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (cart.getTotalItems() == null || !cart.getTotalItems().equals(calculatedItems)) {
            return EvaluationOutcome.fail("Cart total items count is incorrect", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
