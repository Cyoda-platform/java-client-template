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
public class OrderValidForFulfillmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidForFulfillmentCriterion(SerializerFactory serializerFactory) {
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

        if (order.getLines() == null || order.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Order has no line items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact() == null) {
            return EvaluationOutcome.fail("Order missing guest contact information", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact().getName() == null || order.getGuestContact().getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order missing guest name", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getGuestContact().getAddress() == null) {
            return EvaluationOutcome.fail("Order missing shipping address", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Order.GuestAddress address = order.getGuestContact().getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order missing address line 1", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order missing city", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order missing postcode", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order missing country", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getTotals() == null || order.getTotals().getGrand() == null || order.getTotals().getGrand() <= 0) {
            return EvaluationOutcome.fail("Order has invalid total amount", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}