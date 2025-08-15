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
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
    private final EntityService entityService;

    public StockAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
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

        try {
            for (ShoppingCart.CartItem it : cart.getItems()) {
                if (it.getProductId() == null || it.getProductId().isBlank()) return EvaluationOutcome.fail("productId missing in cart item", StandardEvalReasonCategories.VALIDATION_FAILURE);
                if (it.getQuantity() == null || it.getQuantity() <= 0) return EvaluationOutcome.fail("invalid quantity in cart item", StandardEvalReasonCategories.VALIDATION_FAILURE);

                // Authoritative check: query product by business id and ensure effective availability
                com.fasterxml.jackson.databind.node.ArrayNode found = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", it.getProductId())),
                    true
                ).get();

                if (found == null || found.size() == 0) {
                    return EvaluationOutcome.fail("product not found: " + it.getProductId(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
                com.fasterxml.jackson.databind.node.ObjectNode pnode = (com.fasterxml.jackson.databind.node.ObjectNode) found.get(0);
                int stock = pnode.has("stockQuantity") ? pnode.get("stockQuantity").asInt() : 0;
                int reserved = pnode.has("reservedQuantity") ? pnode.get("reservedQuantity").asInt() : 0;
                int effective = stock - reserved;
                if (effective < it.getQuantity()) {
                    return EvaluationOutcome.fail("insufficient stock for product " + it.getProductId() + ": required=" + it.getQuantity() + " available=" + effective, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        } catch (Exception ex) {
            logger.error("Error checking stock availability", ex);
            return EvaluationOutcome.fail("error checking stock", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
