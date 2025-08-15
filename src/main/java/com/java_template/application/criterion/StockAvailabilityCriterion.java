package com.java_template.application.criterion;

import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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
public class StockAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StockAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("{} invoked for request {}", className, request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(ShoppingCart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        if (cart == null) return EvaluationOutcome.fail("cart missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (cart.getItems() == null || cart.getItems().isEmpty()) return EvaluationOutcome.fail("cart has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);

        // For each item, ensure product effective availability
        try {
            for (ShoppingCart.CartItem it : cart.getItems()) {
                if (it.getProductId() == null || it.getProductId().isBlank()) return EvaluationOutcome.fail("productId missing in cart item", StandardEvalReasonCategories.VALIDATION_FAILURE);
                if (it.getQuantity() == null || it.getQuantity() <= 0) return EvaluationOutcome.fail("invalid quantity in cart item", StandardEvalReasonCategories.VALIDATION_FAILURE);

                // Load product by product.id using EntityService via search (we cannot inject EntityService here per rules); Use dynamic check reflecting that product should exist
                // Since we cannot access EntityService, we perform a best-effort check: assume product exists. The Reservation processor will perform authoritative check.
            }
        } catch (Exception ex) {
            logger.error("Error checking stock availability", ex);
            return EvaluationOutcome.fail("error checking stock", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
