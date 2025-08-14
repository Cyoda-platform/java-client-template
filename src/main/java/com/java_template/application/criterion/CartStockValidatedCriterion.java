package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cartitem.version_1.CartItem;
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
public class CartStockValidatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartStockValidatedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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
        if (cart == null) {
            return EvaluationOutcome.fail("Cart entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return EvaluationOutcome.fail("Cart must have at least one item", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        for (CartItem item : cart.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return EvaluationOutcome.fail("Cart item quantity must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!isProductAvailable(item.getProductId(), item.getQuantity())) {
                return EvaluationOutcome.fail("Product " + item.getProductId() + " is not available in sufficient quantity", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }

    private boolean isProductAvailable(String productId, Integer quantity) {
        // TODO: Implement actual stock check logic with inventory service
        // For now, assume all products are available
        return true;
    }
}
