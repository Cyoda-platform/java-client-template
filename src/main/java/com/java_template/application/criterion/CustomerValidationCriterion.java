package com.java_template.application.criterion;

import com.example.application.entity.customer.version_1.Customer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import lombok.extern.slf4j.Slf4j;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;

/**
 * Customer Validation Criterion
 * Validates customer data for workflow transitions
 */
@Slf4j
@Component
public class CustomerValidationCriterion implements CyodaCriterion {

    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CustomerValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        log.debug("Checking customer validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Customer.class, this::validateCustomer)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCustomer(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();
        log.debug("Validating customer: {}", customer.getCustomerId());

        // Check if entity is null
        if (customer == null) {
            log.warn("Customer is null");
            return EvaluationOutcome.fail("Customer is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check required fields
        if (customer.getCustomerId() == null) {
            return EvaluationOutcome.fail("Customer ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getFirstName() == null || customer.getFirstName().isBlank()) {
            return EvaluationOutcome.fail("First name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getLastName() == null || customer.getLastName().isBlank()) {
            return EvaluationOutcome.fail("Last name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getPhone() == null || customer.getPhone().isBlank()) {
            return EvaluationOutcome.fail("Phone is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getDateOfBirth() == null) {
            return EvaluationOutcome.fail("Date of birth is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getAddress() == null) {
            return EvaluationOutcome.fail("Address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (customer.getKyc() == null) {
            return EvaluationOutcome.fail("KYC information is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        log.info("Customer {} validation passed", customer.getCustomerId());
        return EvaluationOutcome.success();
    }
}

