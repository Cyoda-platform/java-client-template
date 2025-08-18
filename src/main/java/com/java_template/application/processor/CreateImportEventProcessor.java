package com.java_template.application.processor;

import com.java_template.application.entity.importevent.version_1.ImportEvent;
import com.java_template.application.store.InMemoryDataStore;
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

import java.time.Instant;
import java.util.UUID;

@Component
public class CreateImportEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateImportEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateImportEventProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Creating ImportEvent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportEvent.class)
            .validate(this::isValidEntity, "Invalid import event")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportEvent entity) {
        return entity != null && entity.getEventId() != null;
    }

    private ImportEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportEvent> context) {
        ImportEvent entity = context.entity();
        try {
            if (entity.getTimestamp() == null) {
                entity.setTimestamp(Instant.now().toString());
            }
            if (entity.getEventId() == null) {
                entity.setEventId("event-" + UUID.randomUUID());
            }
            InMemoryDataStore.importEvents.add(entity);
            logger.info("Recorded ImportEvent {}", entity.getEventId());
        } catch (Exception e) {
            logger.error("Error recording import event {}: {}", entity.getEventId(), e.getMessage(), e);
        }
        return entity;
    }
}
