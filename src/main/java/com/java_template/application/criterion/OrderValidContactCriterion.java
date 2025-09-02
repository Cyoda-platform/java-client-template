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
public class OrderValidContactCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidContactCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking order valid contact criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderContact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderContact(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        Order.GuestContact contact = order.getGuestContact();
        if (contact == null) {
            return EvaluationOutcome.fail("Guest contact required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (contact.getName() == null || contact.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Guest name required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        Order.Address address = contact.getAddress();
        if (address == null) {
            return EvaluationOutcome.fail("Address required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Complete address required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("Complete address required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Complete address required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Complete address required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
}
