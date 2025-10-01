package com.java_template.application.criterion;

import com.java_template.application.entity.adverse_event.version_1.AdverseEvent;
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
 * Criterion to determine if an adverse event requires follow-up
 * Evaluates SAE status and severity to determine follow-up requirements
 */
@Component
public class SAEFollowUpCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SAEFollowUpCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SAE follow-up criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(AdverseEvent.class, this::evaluateFollowUpRequirement)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Evaluates whether the adverse event requires follow-up
     * Returns success if follow-up is required, fail if not
     */
    private EvaluationOutcome evaluateFollowUpRequirement(
            CriterionSerializer.CriterionEntityEvaluationContext<AdverseEvent> context) {
        
        AdverseEvent adverseEvent = context.entityWithMetadata().entity();

        // Check if entity is null
        if (adverseEvent == null) {
            logger.warn("AdverseEvent is null");
            return EvaluationOutcome.fail("Adverse event is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!adverseEvent.isValid()) {
            logger.warn("AdverseEvent is not valid");
            return EvaluationOutcome.fail("Adverse event is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if this is an SAE - SAEs always require follow-up
        if (Boolean.TRUE.equals(adverseEvent.getIsSAE())) {
            logger.debug("AE {} is SAE - follow-up required", adverseEvent.getAdverseEventId());
            return EvaluationOutcome.success();
        }

        // Check severity - severe events require follow-up
        if ("severe".equalsIgnoreCase(adverseEvent.getSeverity())) {
            logger.debug("AE {} is severe - follow-up required", adverseEvent.getAdverseEventId());
            return EvaluationOutcome.success();
        }

        // Check if outcome is concerning
        if ("fatal".equalsIgnoreCase(adverseEvent.getOutcome()) ||
            "not_recovered".equalsIgnoreCase(adverseEvent.getOutcome())) {
            logger.debug("AE {} has concerning outcome - follow-up required", adverseEvent.getAdverseEventId());
            return EvaluationOutcome.success();
        }

        // Check relatedness - probable/definite relationship requires follow-up
        if ("probable".equalsIgnoreCase(adverseEvent.getRelatedness()) ||
            "definite".equalsIgnoreCase(adverseEvent.getRelatedness())) {
            logger.debug("AE {} has high relatedness - follow-up required", adverseEvent.getAdverseEventId());
            return EvaluationOutcome.success();
        }

        // No follow-up required
        logger.debug("AE {} does not require follow-up", adverseEvent.getAdverseEventId());
        return EvaluationOutcome.fail("No follow-up required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
