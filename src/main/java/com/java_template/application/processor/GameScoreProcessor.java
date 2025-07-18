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
import java.util.concurrent.ExecutionException;

@Component
public class GameScoreProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public GameScoreProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("GameScoreProcessor initialized with SerializerFactory and EntityService");
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
                "gamescore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private GameScore processEntityLogic(GameScore score) {
        try {
            logger.info("Processing GameScore with technicalId: {}", score.getTechnicalId());
            if ("NEW".equalsIgnoreCase(score.getStatus())) {
                score.setStatus("PROCESSED");
                entityService.updateItem("gamescore", Config.ENTITY_VERSION, score.getTechnicalId(), score).get();
                logger.info("GameScore technicalId {} marked as PROCESSED", score.getTechnicalId());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to process GameScore with technicalId {}", score.getTechnicalId(), e);
            Thread.currentThread().interrupt();
        }
        return score;
    }
}
