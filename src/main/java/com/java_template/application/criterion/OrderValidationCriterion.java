package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
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
        if (order == null) {
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // items
        List<?> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return EvaluationOutcome.fail("Order has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // totals match
        try {
            BigDecimal total = order.getTotalAmount() == null ? null : new BigDecimal(order.getTotalAmount().toString());
            BigDecimal computed = BigDecimal.ZERO;
            if (items != null) {
                for (Object o : items) {
                    if (o instanceof Map) {
                        Map<?,?> m = (Map<?,?>) o;
                        Object q = m.get("quantity");
                        Object p = m.get("price");
                        BigDecimal qty = q == null ? BigDecimal.ZERO : new BigDecimal(q.toString());
                        BigDecimal price = p == null ? BigDecimal.ZERO : new BigDecimal(p.toString());
                        computed = computed.add(price.multiply(qty));
                    }
                }
            }
            if (total == null || computed.compareTo(total) != 0) {
                return EvaluationOutcome.fail("Order total mismatch", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Unable to compute totals: {}", e.getMessage());
            return EvaluationOutcome.fail("Unable to verify totals", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // address basic validation
        try {
            Object shippingAddress = order.getShippingAddress();
            if (shippingAddress == null) {
                return EvaluationOutcome.fail("Shipping address missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (shippingAddress instanceof Map) {
                Map<?,?> addr = (Map<?,?>) shippingAddress;
                if (addr.get("line1") == null || addr.get("city") == null || addr.get("postalCode") == null || addr.get("country") == null) {
                    return EvaluationOutcome.fail("Incomplete shipping address", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to inspect shipping address: {}", e.getMessage());
            return EvaluationOutcome.fail("Unable to inspect shipping address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
