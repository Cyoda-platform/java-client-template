package com.java_template.application.processor;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class CompleteJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompleteJobProcessor(SerializerFactory serializerFactory,
                                EntityService entityService,
                                ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklySendJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklySendJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Custom validation for CompleteJobProcessor:
     * - entity must be non-null
     * - must reference a catFactTechnicalId
     * - must have a status that is eligible for completion (DISPATCHED or RUNNING)
     *
     * Do NOT rely on WeeklySendJob.isValid() here because runAt may be missing prior to completion
     */
    private boolean isValidEntity(WeeklySendJob entity) {
        if (entity == null) return false;

        String catFactId = entity.getCatFactTechnicalId();
        if (catFactId == null || catFactId.isBlank()) {
            logger.debug("WeeklySendJob validation failed: missing catFactTechnicalId");
            return false;
        }

        String status = entity.getStatus();
        if (status == null || status.isBlank()) {
            logger.debug("WeeklySendJob validation failed: missing status");
            return false;
        }

        // Only allow completion for jobs that are dispatched or running
        if (!"DISPATCHED".equalsIgnoreCase(status) && !"RUNNING".equalsIgnoreCase(status)) {
            logger.debug("WeeklySendJob with status '{}' is not eligible for completion", status);
            return false;
        }

        return true;
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob entity = context.entity();

        // Business logic:
        // - Ensure there is a catFactTechnicalId associated with the job.
        // - Verify referenced CatFact exists. If missing -> mark job FAILED and populate errorMessage.
        // - Only finalize job to COMPLETED when referenced CatFact exists.
        // - Set runAt to current time if missing.
        // - Clear errorMessage on success.

        try {
            // Ensure runAt is set (record actual dispatched/run time if missing)
            if (entity.getRunAt() == null || entity.getRunAt().isBlank()) {
                entity.setRunAt(Instant.now().toString());
                logger.debug("runAt was missing. Set runAt to {}", entity.getRunAt());
            }

            String catFactIdStr = entity.getCatFactTechnicalId();
            if (catFactIdStr == null || catFactIdStr.isBlank()) {
                logger.error("WeeklySendJob {} missing catFactTechnicalId; marking as FAILED", context.request().getId());
                entity.setStatus("FAILED");
                entity.setErrorMessage("Missing catFactTechnicalId for job completion.");
                return entity;
            }

            // Attempt to retrieve the CatFact by technical id to ensure it exists
            try {
                UUID catFactUuid = UUID.fromString(catFactIdStr);
                CompletableFuture<DataPayload> payloadFuture = entityService.getItem(catFactUuid);
                DataPayload payload = payloadFuture.get();

                if (payload == null || payload.getData() == null) {
                    logger.error("CatFact with id {} not found for WeeklySendJob {}", catFactIdStr, context.request().getId());
                    entity.setStatus("FAILED");
                    entity.setErrorMessage("Referenced CatFact not found: " + catFactIdStr);
                    return entity;
                }

                // Optionally validate CatFact state (e.g., ensure validationStatus is VALID) before completing
                try {
                    CatFact catFact = objectMapper.treeToValue(payload.getData(), CatFact.class);
                    if (catFact == null) {
                        entity.setStatus("FAILED");
                        entity.setErrorMessage("Referenced CatFact could not be deserialized: " + catFactIdStr);
                        return entity;
                    }
                    // If the cat fact exists, mark job completed
                    entity.setStatus("COMPLETED");
                    entity.setErrorMessage(null);
                    logger.info("WeeklySendJob {} completed successfully for CatFact {}", context.request().getId(), catFactIdStr);
                    return entity;
                } catch (Exception ex) {
                    logger.error("Failed to map CatFact payload for id {}: {}", catFactIdStr, ex.getMessage(), ex);
                    entity.setStatus("FAILED");
                    entity.setErrorMessage("Failed to deserialize referenced CatFact: " + ex.getMessage());
                    return entity;
                }

            } catch (IllegalArgumentException iae) {
                logger.error("Invalid UUID format for catFactTechnicalId '{}' in WeeklySendJob {}",
                        entity.getCatFactTechnicalId(), context.request().getId(), iae);
                entity.setStatus("FAILED");
                entity.setErrorMessage("Invalid catFactTechnicalId format: " + entity.getCatFactTechnicalId());
                return entity;
            }
        } catch (Exception e) {
            logger.error("Unexpected error while completing WeeklySendJob {}: {}", context.request().getId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setErrorMessage("Unexpected error in CompleteJobProcessor: " + e.getMessage());
            return entity;
        }
    }
}