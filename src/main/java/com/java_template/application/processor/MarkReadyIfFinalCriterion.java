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
public class MarkReadyIfFinalCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkReadyIfFinalCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkReadyIfFinalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Game finalization for request: {}", request.getId());

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
            if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("final")) {
                // Use rawPayload to set ready flag? The Game entity has no ready_for_notification field; this processor logs and sets lastUpdated
                entity.setLastUpdated(java.time.Instant.now().toString());
            }
        } catch (Exception e) {
            logger.error("Error in MarkReadyIfFinalCriterion: {}", e.getMessage(), e);
        }
        return entity;
    }
}
