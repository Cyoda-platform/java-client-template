package com.java_template.application.criterion;

import com.java_template.application.entity.interaction.version_1.Interaction;
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
 * ABOUTME: Criterion for validating interaction data before recording.
 * Checks interaction type and required fields.
 */
@Component
public class InteractionValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InteractionValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Interaction validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Interaction.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Interaction> context) {
        Interaction entity = context.entityWithMetadata().entity();

        if (entity == null) {
            logger.warn("Interaction is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("Interaction is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate interaction type
        String interactionType = entity.getInteractionType().toLowerCase();
        if (!isValidInteractionType(interactionType)) {
            logger.warn("Invalid interaction type: {}", entity.getInteractionType());
            return EvaluationOutcome.fail("Invalid interaction type", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean isValidInteractionType(String type) {
        return type.equals("open") || type.equals("click") || type.equals("unsubscribe");
    }
}

