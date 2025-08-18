package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistLaureateEventsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateEventsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistLaureateEventsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistLaureateEvents for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate record")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getFullName() != null && !laureate.getFullName().isBlank();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        try {
            // Ensure technical/business ids
            if (laureate.getId() == null || laureate.getId().isBlank()) {
                String generated = "laur-" + UUID.randomUUID().toString();
                laureate.setId(generated);
            }
            if (laureate.getCurrentVersion() == null) {
                laureate.setCurrentVersion(1);
            }
            if (laureate.getCreatedAt() == null) {
                laureate.setCreatedAt(Instant.now().toString());
            }

            // Determine changeType: NEW if businessId empty or marked as NEW by upstream
            if (laureate.getChangeType() == null || laureate.getChangeType().isBlank()) {
                laureate.setChangeType("NEW");
            }

            // Persist laureate entity via entityService.addItem
            try {
                CompletableFuture<java.util.UUID> fut = entityService.addItem(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), laureate);
                fut.whenComplete((uuid, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to persist laureate {}: {}", laureate.getId(), ex.getMessage());
                    } else {
                        logger.info("Persisted laureate {} as technical id {}", laureate.getId(), uuid);
                    }
                });
            } catch (Exception e) {
                logger.warn("Error submitting laureate {} for persistence: {}", laureate.getFullName(), e.getMessage());
            }

            // Emit ChangeEvent by delegating to EmitChangeEventProcessor responsibilities: construct ChangeEvent and persist
            try {
                // Build change event minimal fields and persist using EntityService
                com.java_template.application.entity.changeevent.version_1.ChangeEvent evt = new com.java_template.application.entity.changeevent.version_1.ChangeEvent();
                evt.setId("evt-" + UUID.randomUUID().toString());
                evt.setLaureateId(laureate.getId());
                evt.setEventType(laureate.getChangeType());
                try {
                    evt.setPayload(objectMapper.writeValueAsString(laureate));
                } catch (Exception se) {
                    evt.setPayload("{\"laureateId\":\"" + laureate.getId() + "\"}");
                }
                evt.setCreatedAt(Instant.now().toString());
                entityService.addItem(com.java_template.application.entity.changeevent.version_1.ChangeEvent.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.changeevent.version_1.ChangeEvent.ENTITY_VERSION), evt)
                    .whenComplete((u, ex) -> {
                        if (ex != null) logger.warn("Failed to persist change event {}: {}", evt.getId(), ex.getMessage());
                        else logger.info("Persisted ChangeEvent {}", evt.getId());
                    });
            } catch (Exception e) {
                logger.warn("Error emitting ChangeEvent for laureate {}: {}", laureate.getId(), e.getMessage());
            }

            logger.info("PersistLaureateEventsProcessor completed for laureate {}", laureate.getId());
        } catch (Exception e) {
            logger.error("Error in PersistLaureateEventsProcessor for {}: {}", laureate, e.getMessage());
        }
        return laureate;
    }
}
