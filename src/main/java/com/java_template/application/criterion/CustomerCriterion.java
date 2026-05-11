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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Criterion for validating Customer entity business rules.
 * Ensures customer data meets all required validation constraints.
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
     * Validates customer entity business rules
     */
    private EvaluationOutcome validateCustomer(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();

        // Structural validation
        if (customer == null) {
            logger.warn("Customer is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Business validation
        if (!customer.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Customer is not valid");
            return EvaluationOutcome.fail("Customer data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

