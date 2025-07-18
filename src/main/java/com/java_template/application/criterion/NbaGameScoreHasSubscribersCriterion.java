package com.java_template.application.criterion;

import com.java_template.application.entity.NbaGameScore;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NbaGameScoreHasSubscribersCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public NbaGameScoreHasSubscribersCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("NbaGameScoreHasSubscribersCriterion initialized with CriterionSerializer");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        
        return serializer.withRequest(request)
            .evaluateEntity(NbaGameScore.class, this::applyValidation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome applyValidation(NbaGameScore score) {
        // Placeholder logic: since Subscriber entity is not queried here, assume always has subscribers.
        // Real implementation would check subscriber repository or service for matching subscribers.
        boolean hasSubscribers = true; // Placeholder
        if (hasSubscribers) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("No subscribers found", com.java_template.common.serializer.StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaGameScoreHasSubscribersCriterion".equals(modelSpec.operationName()) &&
               "nbagamescore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
