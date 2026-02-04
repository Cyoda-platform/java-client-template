package com.java_template.application.processor;

import com.java_template.application.entity.event.version_1.Event;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * TrackEventProcessor - Handles event tracking and recording
 * 
 * This processor validates event data and ensures timestamps are properly set.
 */
@Component
public class TrackEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TrackEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TrackEventProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Event for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Event.class)
                .validate(this::isValidEvent, "Invalid Event")
                .map(ctx -> {
                    Event event = ctx.entityResponse().entity();
                    // Ensure timestamp is set
                    if (event.getTimestamp() == null) {
                        event.setTimestamp(LocalDateTime.now());
                    }
                    logger.info("Event tracked: {} - {}", event.getEventId(), event.getType());
                    return ctx.entityResponse();
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEvent(com.java_template.common.dto.EntityWithMetadata<Event> entityWithMetadata) {
        Event event = entityWithMetadata.entity();
        return event != null && event.isValid(entityWithMetadata.metadata());
    }
}

