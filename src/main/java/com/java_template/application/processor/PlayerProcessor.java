package com.java_template.application.processor;

import com.java_template.application.entity.Player;
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

@Component
public class PlayerProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PlayerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PlayerProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Player for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Player.class)
                .withErrorHandler(this::handlePlayerError)
                .validate(this::isValidPlayer, "Invalid Player entity state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PlayerProcessor".equals(modelSpec.operationName()) &&
               "player".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPlayer(Player player) {
        return player.isValid();
    }

    private ErrorInfo handlePlayerError(Throwable t, Player player) {
        logger.error("Error processing Player entity", t);
        return new ErrorInfo("PLAYER_PROCESSOR_ERROR", t.getMessage());
    }
}
