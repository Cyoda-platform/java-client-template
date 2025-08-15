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

import java.util.Optional;

@Component
public class InventoryInsufficientCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InventoryInsufficientCriterion(SerializerFactory serializerFactory) {
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
            return EvaluationOutcome.fail("Cart missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // We cannot query real Product stock from here; instead use cart item snapshots to infer availability.
        boolean anyInsufficient = false;
        for (var it : cart.getItems()) {
            Integer qty = it.getQuantity();
            if (qty == null || qty <= 0) {
                return EvaluationOutcome.fail("Invalid item quantity", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            // If unitPrice is null, we consider product missing and treat as insufficient
            if (it.getUnitPrice() == null) {
                anyInsufficient = true;
                break;
            }
        }

        if (anyInsufficient) {
            return EvaluationOutcome.fail("Inventory insufficient or product snapshot missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
