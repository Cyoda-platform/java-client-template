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
                .validate(GameScore::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "GameScoreProcessor".equals(modelSpec.operationName()) &&
                "gameScore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private GameScore processEntityLogic(GameScore gameScore) {
        logger.info("Processing GameScore with ID: {}", gameScore.getGameId());
        if (gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()) {
            logger.error("Invalid GameScore data: missing team names");
            return gameScore;
        }
        if (gameScore.getHomeScore() == null || gameScore.getAwayScore() == null) {
            logger.error("Invalid GameScore data: missing scores");
            return gameScore;
        }
        logger.info("GameScore {} processed successfully", gameScore.getGameId());
        // No field modifications as per prototype
        return gameScore;
    }
}
