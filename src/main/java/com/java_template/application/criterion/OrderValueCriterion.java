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

@Component
public class OrderValueCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValueCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating OrderValueCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderValue)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderValue(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Validating order value for order: {}", order != null ? order.getOrderId() : "null");

        if (order == null) {
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check order.totals.grand > 0
        if (order.getTotals() == null || order.getTotals().getGrand() == null || order.getTotals().getGrand() <= 0) {
            return EvaluationOutcome.fail("Order total must be greater than zero", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order.lines is not empty
        if (order.getLines() == null || order.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Order must have line items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check all lines have lineTotal > 0
        for (Order.OrderLine line : order.getLines()) {
            if (line.getLineTotal() == null || line.getLineTotal() <= 0) {
                return EvaluationOutcome.fail("Invalid line total calculation for SKU: " + line.getSku(), 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Check sum of line totals == order.totals.grand
        double calculatedGrandTotal = order.getLines().stream()
            .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
            .sum();
        
        if (Math.abs(calculatedGrandTotal - order.getTotals().getGrand()) > 0.01) { // Allow for small floating point differences
            return EvaluationOutcome.fail("Order total calculation mismatch: calculated=" + calculatedGrandTotal + 
                ", stored=" + order.getTotals().getGrand(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order.totals.items > 0
        if (order.getTotals().getItems() == null || order.getTotals().getItems() <= 0) {
            return EvaluationOutcome.fail("Order item count is invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check sum of line quantities == order.totals.items
        int calculatedTotalItems = order.getLines().stream()
            .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
            .sum();
        
        if (calculatedTotalItems != order.getTotals().getItems()) {
            return EvaluationOutcome.fail("Item count calculation mismatch: calculated=" + calculatedTotalItems + 
                ", stored=" + order.getTotals().getItems(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Order value validation passed: orderId={}, grandTotal={}, totalItems={}", 
            order.getOrderId(), order.getTotals().getGrand(), order.getTotals().getItems());

        return EvaluationOutcome.success();
    }
}
