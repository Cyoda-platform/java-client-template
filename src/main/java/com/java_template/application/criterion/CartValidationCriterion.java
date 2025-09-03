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
public class CartValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Validating cart for request: {}", request.getId());
        
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        // Validate cart has at least one line item
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Cart must have at least one line item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate total items is greater than 0
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            return EvaluationOutcome.fail("Cart total items must be greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate grand total is greater than 0
        if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
            return EvaluationOutcome.fail("Cart grand total must be greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate all line items have valid data
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return EvaluationOutcome.fail("All cart lines must have a valid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (line.getQty() == null || line.getQty() <= 0) {
                return EvaluationOutcome.fail("All cart lines must have quantity greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (line.getPrice() == null || line.getPrice() < 0) {
                return EvaluationOutcome.fail("All cart lines must have a valid price", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        
        logger.info("Cart validation passed for cart: {}", cart.getCartId());
        return EvaluationOutcome.success();
    }
}
