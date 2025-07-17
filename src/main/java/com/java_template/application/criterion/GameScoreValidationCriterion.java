package com.java_template.application.criterion;

import com.java_template.application.entity.GameScore;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
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

import java.util.function.BiFunction;

@Component
public class GameScoreValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public GameScoreValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("GameScoreValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking GameScore validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(GameScore.class, this::validateGameScore)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler(this::handleValidationError)
                .complete();
    }

    private EvaluationOutcome validateGameScore(GameScore gameScore) {
        if (gameScore == null) {
            return EvaluationOutcome.fail("GameScore entity is null");
        }
        if (gameScore.getDate() == null || gameScore.getDate().isBlank()) {
            return EvaluationOutcome.fail("Date must not be empty");
        }
        if (gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()) {
            return EvaluationOutcome.fail("Home team must not be empty");
        }
        if (gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()) {
            return EvaluationOutcome.fail("Away team must not be empty");
        }
        if (gameScore.getHomeScore() != null && gameScore.getHomeScore() < 0) {
            return EvaluationOutcome.fail("Home score cannot be negative");
        }
        if (gameScore.getAwayScore() != null && gameScore.getAwayScore() < 0) {
            return EvaluationOutcome.fail("Away score cannot be negative");
        }
        return EvaluationOutcome.success();
    }

    private ErrorInfo handleValidationError(Throwable error, GameScore gameScore) {
        logger.debug("GameScore validation failed for request: {}", error.getMessage(), error);
        return ErrorInfo.validationError("GameScore validation failed: " + error.getMessage());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "GameScoreValidationCriterion".equals(modelSpec.operationName()) &&
                "gameScore".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
