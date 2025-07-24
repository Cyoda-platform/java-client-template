package com.java_template.application.processor;

import com.java_template.application.entity.GameScore;
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
public class TriggerNotificationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public TriggerNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("TriggerNotificationProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TriggerNotification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(GameScore.class)
                .map(gameScore -> {
                    // Business logic from prototype: log processing game score
                    logger.info("Processing GameScore for game: {} vs {} on {}",
                            gameScore.getHomeTeam(), gameScore.getAwayTeam(), gameScore.getGameDate());
                    // No modifications done to entity
                    return gameScore;
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "TriggerNotificationProcessor".equals(modelSpec.operationName()) &&
                "gamescore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
