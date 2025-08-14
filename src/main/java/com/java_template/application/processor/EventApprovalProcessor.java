package com.java_template.application.processor;

import com.java_template.application.entity.event.version_1.Event;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EventApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EventApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EventApprovalProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Event Approval for request: {}", request.getId());

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

    private boolean isValidEntity(Event event) {
        return event != null && event.getEventName() != null && !event.getEventName().isEmpty();
    }

    private Event processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Event> context) {
        Event event = context.entity();

        // Business logic:
        // Approve event: set status to "APPROVED"
        event.setEventType(event.getEventType() != null ? event.getEventType() : "General");

        try {
            java.lang.refl ect .Method setStatus = event.getClass().getMethod("setStatus", String.class);
            setStatus.invoke(event, "APPROVED");
        } catch (NoSuchMethodException e) {
            logger.warn("Event entity has no 'status' field to set");
        } catch (Exception e) {
            logger.error("Failed to set status on Event entity", e);
        }

        return event;
    }
}
