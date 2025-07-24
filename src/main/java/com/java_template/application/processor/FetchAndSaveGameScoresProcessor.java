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

import java.util.ArrayList;
import java.util.List;

@Component
public class FetchAndSaveGameScoresProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FetchAndSaveGameScoresProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FetchAndSaveGameScoresProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GameScore fetch and save for request: {}", request.getId());

        // Actual processing logic would involve fetching and saving game scores
        // Here we simulate saving received game scores with status RECEIVED

        return serializer.withRequest(request)
                .toEntity(GameScore.class)
                .map(gameScore -> {
                    // The prototype logic did not elaborate, so we assume initial save with status RECEIVED
                    if (gameScore.getStatus() == null || gameScore.getStatus().isBlank()) {
                        gameScore.setStatus("RECEIVED");
                    }
                    return gameScore;
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchAndSaveGameScoresProcessor".equals(modelSpec.operationName()) &&
                "gamescore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
