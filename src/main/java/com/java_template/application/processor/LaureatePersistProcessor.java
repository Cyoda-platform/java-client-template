package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

@Component
public class LaureatePersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureatePersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureatePersistProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Ensure timestamps and change summary are initialized for a newly persisted Laureate.
        String now = Instant.now().toString();

        // Set firstSeenTimestamp only if it's missing (initial import)
        if (entity.getFirstSeenTimestamp() == null || entity.getFirstSeenTimestamp().isBlank()) {
            entity.setFirstSeenTimestamp(now);
        }

        // Always update lastSeenTimestamp to indicate the latest observation
        entity.setLastSeenTimestamp(now);

        // Provide a sensible default change summary when not supplied
        if (entity.getChangeSummary() == null || entity.getChangeSummary().isBlank()) {
            entity.setChangeSummary("initial import");
        } else {
            // If already present, append a short note that it was processed (keeps existing summary)
            // Avoid overly long summaries; keep concise appended note
            if (!entity.getChangeSummary().contains("processed")) {
                entity.setChangeSummary(entity.getChangeSummary() + " | processed");
            }
        }

        // No other entities should be modified here (per rules). The Laureate entity will be persisted automatically.
        return entity;
    }
}