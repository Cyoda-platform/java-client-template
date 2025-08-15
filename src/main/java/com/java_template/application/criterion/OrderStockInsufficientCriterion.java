package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.OrderItem;
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
public class OrderStockInsufficientCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderStockInsufficientCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        if (order == null) return EvaluationOutcome.fail("Order missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (order.getItems() == null || order.getItems().isEmpty()) return EvaluationOutcome.fail("Order has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        // Prototype: assume we cannot check external Product stock; use item.unitPrice negative to indicate insufficient
        for (OrderItem item : order.getItems()) {
            if (item == null) continue;
            if (item.getUnitPrice() != null && item.getUnitPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
                return EvaluationOutcome.success();
            }
        }
        return EvaluationOutcome.fail("Stock appears sufficient", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}