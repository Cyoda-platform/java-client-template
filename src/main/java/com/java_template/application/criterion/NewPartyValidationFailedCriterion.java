package com.java_template.application.criterion;

import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Negative criterion that succeeds when NewPartyValidationCriterion fails.
 */
@Component
public class NewPartyValidationFailedCriterion extends NewPartyValidationCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NewPartyValidationFailedCriterion(SerializerFactory serializerFactory) {
        super(serializerFactory);
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if new Party validation FAILED for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Party.class, this::validateEntityFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntityFailed(CriterionSerializer.CriterionEntityEvaluationContext<Party> context) {
        EvaluationOutcome parentOutcome = super.validateEntity(context);
        
        if (parentOutcome.isSuccess()) {
            return EvaluationOutcome.fail("Validation passed - error path not needed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            return EvaluationOutcome.success();
        }
    }
}

