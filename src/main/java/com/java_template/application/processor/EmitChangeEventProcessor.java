package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EmitChangeEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmitChangeEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public EmitChangeEventProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmitChangeEvent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for change event")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && (laureate.getId() != null && !laureate.getId().isBlank());
    }

    private ChangeEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        ChangeEvent evt = new ChangeEvent();
        try {
            String eventId = "evt-" + UUID.randomUUID().toString();
            evt.setId(eventId);
            evt.setLaureateId(laureate.getId());
            evt.setEventType(laureate.getChangeType());
            // Serialize payload safely
            try {
                evt.setPayload(objectMapper.writeValueAsString(laureate));
            } catch (Exception se) {
                logger.warn("Failed to serialize laureate payload for event {}: {}", eventId, se.getMessage());
                // fallback to minimal payload
                evt.setPayload("{\"laureateId\":\"" + laureate.getId() + "\"}");
            }
            evt.setCreatedAt(Instant.now().toString());

            logger.info("Constructed ChangeEvent {} for laureate {} changeType={}", evt.getId(), laureate.getId(), laureate.getChangeType());

            // Persist the ChangeEvent asynchronously so workflow can continue. Log failures.
            try {
                entityService.addItem(ChangeEvent.ENTITY_NAME, String.valueOf(ChangeEvent.ENTITY_VERSION), evt)
                    .whenComplete((uuid, ex) -> {
                        if (ex != null) {
                            logger.warn("Failed to persist ChangeEvent {}: {}", evt.getId(), ex.getMessage());
                        } else {
                            logger.info("Persisted ChangeEvent {} as technical id {}", evt.getId(), uuid);
                        }
                    });
            } catch (Exception e) {
                logger.warn("Error submitting ChangeEvent {} for persistence: {}", evt.getId(), e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error emitting change event for laureate {}: {}", laureate.getId(), e.getMessage());
        }
        return evt;
    }
}
