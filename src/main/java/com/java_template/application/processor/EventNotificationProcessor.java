package com.java_template.application.processor;

import com.java_template.application.entity.event.version_1.Event;
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
public class EventNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EventNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EventNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Event Notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Event.class)
            .validate(this::isValidEntity, "Invalid event state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Event entity) {
        return entity != null && entity.isValid();
    }

    private Event processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Event> context) {
        Event entity = context.entity();
        // TODO: Implement notification logic
        // For example, notify subscribers or interested users about event updates
        logger.info("Notifying subscribers about event: {}", entity.getTechnicalId());
        // Notification logic here
        return entity;
    }
}
