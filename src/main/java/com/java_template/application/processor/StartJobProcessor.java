package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
import java.util.HashMap;
import java.util.Map;

@Component
public class StartJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob entity) {
        return entity != null && entity.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob entity = context.entity();

        // Business logic for starting a job:
        // - Move job to FETCHING state so downstream processors know to start fetching users.
        // - Update lastRunTimestamp to current time (ISO-8601 string).
        // - Ensure metadata map exists and record triggering details (non-invasive).
        // Note: Do NOT perform any add/update/delete operations on this BatchJob via EntityService.
        String now = Instant.now().toString();

        // Set status to indicate fetching has been triggered
        entity.setStatus("FETCHING");

        // Update last run timestamp
        entity.setLastRunTimestamp(now);

        // Ensure metadata exists and record trigger info
        Map<String, Object> metadata = entity.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            entity.setMetadata(metadata);
        }

        metadata.put("fetchTriggeredAt", now);
        metadata.put("fetchInitiator", className);

        // If there's a previous fetchAttemptCount, increment it; otherwise initialize to 1
        Object attemptsObj = metadata.get("fetchAttemptCount");
        if (attemptsObj instanceof Number) {
            int attempts = ((Number) attemptsObj).intValue();
            metadata.put("fetchAttemptCount", attempts + 1);
        } else {
            metadata.put("fetchAttemptCount", 1);
        }

        // Initialize fetched_count to 0 to be updated by FetchUsersProcessor later
        if (!metadata.containsKey("fetched_count")) {
            metadata.put("fetched_count", 0);
        }

        return entity;
    }
}