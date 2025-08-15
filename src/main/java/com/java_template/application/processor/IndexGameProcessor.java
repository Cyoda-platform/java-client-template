package com.java_template.application.processor;

import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IndexGameProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexGameProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexGameProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Indexing Game for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Game.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Game entity) {
        return entity != null && entity.isValid();
    }

    private Game processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Game> context) {
        Game entity = context.entity();
        try {
            // This would normally index to external search/caching. Here we only log to indicate indexing occurred.
            logger.info("Indexed game {} on {}", entity.getGameId(), entity.getDate());
        } catch (Exception e) {
            logger.error("Error in IndexGameProcessor: {}", e.getMessage(), e);
        }
        return entity;
    }
}
