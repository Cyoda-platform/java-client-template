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
public class OrderValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrder(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();

        if (order == null) {
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact() == null) {
            return EvaluationOutcome.fail("Order must have guest contact", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact().getName() == null || order.getGuestContact().getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Guest name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact().getAddress() == null) {
            return EvaluationOutcome.fail("Guest address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Order.Address address = order.getGuestContact().getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("City is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Postcode is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getLines() == null || order.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Order must have line items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        double calculatedTotal = 0.0;
        int calculatedItems = 0;

        for (Order.OrderLine line : order.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return EvaluationOutcome.fail("Order line must have valid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQty() == null || line.getQty() <= 0) {
                return EvaluationOutcome.fail("Order line quantity must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getUnitPrice() == null || line.getUnitPrice() < 0) {
                return EvaluationOutcome.fail("Order line unit price cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            double expectedLineTotal = line.getUnitPrice() * line.getQty();
            if (line.getLineTotal() == null || Math.abs(line.getLineTotal() - expectedLineTotal) > 0.01) {
                return EvaluationOutcome.fail("Order line total is incorrect", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            calculatedTotal += line.getLineTotal();
            calculatedItems += line.getQty();
        }

        if (order.getTotals() == null) {
            return EvaluationOutcome.fail("Order totals are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getTotals().getGrand() == null || Math.abs(order.getTotals().getGrand() - calculatedTotal) > 0.01) {
            return EvaluationOutcome.fail("Order grand total is incorrect", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getTotals().getItems() == null || !order.getTotals().getItems().equals(calculatedItems)) {
            return EvaluationOutcome.fail("Order total items count is incorrect", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
