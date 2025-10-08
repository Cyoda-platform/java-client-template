package com.java_template.application.criterion;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Negative criterion that succeeds when NewLoanValidationCriterion fails.
 * This criterion is used to route entities to error states when validation fails,
 * allowing the validation failure reason to be captured and attached to the entity.
 */
@Component
public class NewLoanValidationFailedCriterion extends NewLoanValidationCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NewLoanValidationFailedCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        super(serializerFactory, entityService);
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if new Loan validation FAILED for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::validateEntityFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Inverts the validation logic - succeeds when parent validation fails
     */
    private EvaluationOutcome validateEntityFailed(CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        // Call parent validation logic
        EvaluationOutcome parentOutcome = super.validateEntity(context);

        // Invert the result
        if (parentOutcome.isSuccess()) {
            // Parent validation succeeded, so this negative criterion should fail (don't take error path)
            return EvaluationOutcome.fail("Validation passed - error path not needed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            // Parent validation failed, so this negative criterion should succeed (take error path)
            // Return success so the transition is taken, but the warnings are still attached
            return EvaluationOutcome.success();
        }
    }
}

