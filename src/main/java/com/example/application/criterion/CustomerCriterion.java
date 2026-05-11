package com.example.application.criterion;

import com.example.application.entity.customer.version_1.Customer;
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

/**
 * Customer Criterion - Validates customer entity
 * Checks business rules and data consistency
 */
@Component
public class CustomerCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CustomerCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Customer criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Customer.class, this::validateCustomer)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates customer entity
     */
    private EvaluationOutcome validateCustomer(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();

        if (customer == null) {
            logger.warn("Customer is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!customer.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Customer is not valid");
            return EvaluationOutcome.fail("Customer is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional validation: check email format
        if (!isValidEmail(customer.getEmail())) {
            logger.warn("Invalid email format: {}", customer.getEmail());
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}

