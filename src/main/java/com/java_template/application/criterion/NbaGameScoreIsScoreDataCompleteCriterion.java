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
public class NbaGameScoreIsScoreDataCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public NbaGameScoreIsScoreDataCompleteCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("NbaGameScoreIsScoreDataCompleteCriterion initialized with CriterionSerializer");
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
        if (score.getGameId() == null || score.getGameId().isBlank()) {
            return EvaluationOutcome.fail("Game ID is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getDate() == null) {
            return EvaluationOutcome.fail("Game date is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getHomeTeam() == null || score.getHomeTeam().isBlank()) {
            return EvaluationOutcome.fail("Home team is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getAwayTeam() == null || score.getAwayTeam().isBlank()) {
            return EvaluationOutcome.fail("Away team is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getHomeScore() == null) {
            return EvaluationOutcome.fail("Home score is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getAwayScore() == null) {
            return EvaluationOutcome.fail("Away score is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (score.getStatus() == null || score.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is missing", com.java_template.common.serializer.StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaGameScoreIsScoreDataCompleteCriterion".equals(modelSpec.operationName()) &&
               "nbagamescore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
