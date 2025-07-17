package com.java_template.application.processor;

import com.java_template.application.entity.GameScore;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class GameScoreProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public GameScoreProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("GameScoreProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GameScore for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(GameScore.class)
                .withErrorHandler(this::handleGameScoreError)
                .validate(this::isValidGameScore, "Invalid GameScore entity state")
                .map(this::applyBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "GameScoreProcessor".equals(modelSpec.operationName()) &&
                "gameScore".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidGameScore(GameScore gameScore) {
        return gameScore.isValid();
    }

    private ErrorInfo handleGameScoreError(Throwable throwable, GameScore entity) {
        logger.error("Error processing GameScore: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }

    private GameScore applyBusinessLogic(GameScore gameScore) {
        // Example business logic: ensure no negative scores (redundant with validation, but for demo)
        if (gameScore.getHomeScore() != null && gameScore.getHomeScore() < 0) {
            gameScore.setHomeScore(0);
        }
        if (gameScore.getAwayScore() != null && gameScore.getAwayScore() < 0) {
            gameScore.setAwayScore(0);
        }
        return gameScore;
    }
}
