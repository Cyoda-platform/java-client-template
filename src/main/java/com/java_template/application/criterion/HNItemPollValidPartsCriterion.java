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
 * HNItemPollValidPartsCriterion - Validates that poll items have valid poll option references
 */
@Component
public class HNItemPollValidPartsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HNItemPollValidPartsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HNItem poll valid parts criteria for request: {}", request.getId());
        
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

        if (!"poll".equals(entity.getType())) {
            return EvaluationOutcome.success();
        }

        // Polls must have parts (poll options)
        if (entity.getParts() == null || entity.getParts().isEmpty()) {
            return EvaluationOutcome.fail("Polls must have poll options", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // All parts must be valid IDs
        for (Long partId : entity.getParts()) {
            if (partId == null || partId <= 0) {
                return EvaluationOutcome.fail("All poll parts must be valid IDs", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
