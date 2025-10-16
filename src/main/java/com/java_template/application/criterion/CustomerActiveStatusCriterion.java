package com.java_template.application.criterion;

import com.java_template.application.entity.customer.version_1.Customer;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ABOUTME: Criterion that checks if a customer can place orders,
 * validating the customer is in active state and meets order placement requirements.
 */
@Component
public class CustomerActiveStatusCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(CustomerActiveStatusCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public CustomerActiveStatusCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking customer active status for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Customer.class, this::validateCustomerActiveStatus)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the customer is active and can place orders
     */
    private EvaluationOutcome validateCustomerActiveStatus(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating active status for customer: {} in state: {}", customer.getCustomerId(), currentState);

        // Check if entity is null (structural validation)
        if (customer == null) {
            logger.warn("Customer entity is null");
            return EvaluationOutcome.fail("Customer entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!customer.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Customer entity is not valid");
            return EvaluationOutcome.fail("Customer entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Customer must be in active state
        if (!"active".equals(currentState)) {
            logger.warn("Customer {} is not in active state: {}", customer.getCustomerId(), currentState);
            return EvaluationOutcome.fail("Customer must be in active state to place orders", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Additional business rules can be added here
        // For example: check if customer has valid email, phone, etc.
        if (customer.getEmail() == null || customer.getEmail().trim().isEmpty()) {
            logger.warn("Customer {} does not have a valid email", customer.getCustomerId());
            return EvaluationOutcome.fail("Customer must have a valid email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Customer {} active status check passed", customer.getCustomerId());
        return EvaluationOutcome.success();
    }
}
