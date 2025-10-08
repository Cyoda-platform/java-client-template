package com.java_template.application.criterion.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Negative criterion that succeeds when accrual validation fails.
 * Used to route to error state when validation fails.
 */
@Component
public class AccrualValidationFailedCriterion extends AccrualValidationCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AccrualValidationFailedCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        super(serializerFactory, entityService);
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AccrualValidationFailed (negative criterion) for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateEntityFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntityFailed(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        EvaluationOutcome parentOutcome = super.validateEntity(context);
        
        if (parentOutcome.isSuccess()) {
            // Parent validation succeeded, so this negative criterion should fail (don't take error path)
            return EvaluationOutcome.fail("Validation passed - error path not needed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            // Parent validation failed, so this negative criterion should succeed (take error path)
            return EvaluationOutcome.success();
        }
    }
}

