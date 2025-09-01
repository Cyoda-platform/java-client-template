package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CartHasItemsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartHasItemsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking cart has items criterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartHasItems)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartHasItems(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();

        // Check if cart exists
        if (cart == null) {
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if cart has lines
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if any line has valid quantity
        boolean hasValidItems = cart.getLines().stream()
            .anyMatch(line -> line.getQty() != null && line.getQty() > 0);

        if (!hasValidItems) {
            return EvaluationOutcome.fail("Cart has no items with valid quantities", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Cart {} has {} valid items", cart.getCartId(), cart.getLines().size());
        return EvaluationOutcome.success();
    }
}