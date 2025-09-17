package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
 * HNItemPollOptValidPollCriterion - Validates that poll option items have a valid poll reference
 */
@Component
public class HNItemPollOptValidPollCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HNItemPollOptValidPollCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HNItem poll option valid poll criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entityWithMetadata().entity();

        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!"pollopt".equals(entity.getType())) {
            return EvaluationOutcome.success("Not applicable to non-poll options");
        }

        // Poll options must reference a poll
        if (entity.getPoll() == null || entity.getPoll() <= 0) {
            return EvaluationOutcome.fail("Poll options must reference a valid poll", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Poll reference cannot be the same as the item itself
        if (entity.getPoll().equals(entity.getId())) {
            return EvaluationOutcome.fail("Poll reference cannot be the same as the item itself", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
