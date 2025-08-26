package com.java_template.application.criterion;

import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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
public class CheckoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckoutCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ShoppingCart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ShoppingCart> context) {
         ShoppingCart cart = context.entity();

         if (cart == null) {
             logger.warn("ShoppingCart entity missing in context");
             return EvaluationOutcome.fail("ShoppingCart entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields
         if (cart.getCartId() == null || cart.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (cart.getCustomerUserId() == null || cart.getCustomerUserId().isBlank()) {
             return EvaluationOutcome.fail("customerUserId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (cart.getCreatedAt() == null || cart.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Items list must exist and have at least one item for checkout
         if (cart.getItems() == null || cart.getItems().isEmpty()) {
             return EvaluationOutcome.fail("Shopping cart must contain at least one item to checkout", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate each item
         int index = 0;
         for (ShoppingCart.Item it : cart.getItems()) {
             index++;
             if (it == null) {
                 return EvaluationOutcome.fail("Cart item at position " + index + " is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getProductSku() == null || it.getProductSku().isBlank()) {
                 return EvaluationOutcome.fail("productSku is required for item at position " + index, StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getQuantity() == null) {
                 return EvaluationOutcome.fail("quantity is required for item with sku " + it.getProductSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getQuantity() <= 0) {
                 return EvaluationOutcome.fail("quantity must be greater than zero for item with sku " + it.getProductSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (it.getPriceAtAdd() == null) {
                 return EvaluationOutcome.fail("priceAtAdd is required for item with sku " + it.getProductSku(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (it.getPriceAtAdd() < 0.0) {
                 return EvaluationOutcome.fail("priceAtAdd must not be negative for item with sku " + it.getProductSku(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // All basic validations passed. Note: actual stock availability and current product price checks
         // are out of scope for this criterion because external product state is not accessible here.
         return EvaluationOutcome.success();
    }
}