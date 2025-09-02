package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CartReadyForCheckoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CartReadyForCheckoutCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartReadyForCheckout)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartReadyForCheckout(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();

        if (cart == null) {
            return EvaluationOutcome.fail("Cart not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if cart is in ACTIVE state
        String cartState = context.request().getPayload().getMeta().get("state").toString();
        if (!"ACTIVE".equals(cartState)) {
            return EvaluationOutcome.fail("Cart is not in active state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Cart is empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate all products in cart are still available
        for (Cart.CartLine line : cart.getLines()) {
            Optional<EntityResponse<Product>> productResponse = getProductBySku(line.getSku());
            
            if (productResponse.isEmpty()) {
                return EvaluationOutcome.fail("Product " + line.getSku() + " not found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            Product product = productResponse.get().getData();
            String productState = productResponse.get().getMetadata().getState();

            if (!"ACTIVE".equals(productState)) {
                return EvaluationOutcome.fail("Product " + line.getSku() + " is no longer available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < line.getQty()) {
                return EvaluationOutcome.fail("Insufficient stock for product " + line.getSku(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }

    private Optional<EntityResponse<Product>> getProductBySku(String sku) {
        try {
            Condition condition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
            return entityService.getFirstItemByCondition(Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, searchCondition, true);
        } catch (Exception e) {
            logger.error("Error retrieving product by SKU: {}", sku, e);
            return Optional.empty();
        }
    }
}
