package com.java_template.application.processor;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

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
     * Custom validation that only checks fields necessary for starting the job.
     * We intentionally avoid calling entity.isValid() because the entity's own
     * isValid() may require fields that are populated later in the workflow
     * (for example runAt or catFactTechnicalId).
     */
    private boolean isValidEntity(WeeklySendJob entity) {
        if (entity == null) return false;
        if (entity.getScheduledFor() == null || entity.getScheduledFor().isBlank()) return false;
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) return false;
        // status should be present (typically CREATED). If not present, consider invalid.
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        return true;
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob entity = context.entity();

        // If the job is already beyond CREATED state, do not start it again.
        String currentStatus = entity.getStatus();
        if (currentStatus != null && !currentStatus.equalsIgnoreCase("CREATED")) {
            logger.info("WeeklySendJob {} has status '{}' - StartJobProcessor will not change it.", context.request().getId(), currentStatus);
            return entity;
        }

        // Business logic:
        // - Verify scheduledFor is a valid ISO-8601 datetime.
        // - If scheduledFor is in the future, do not start the job (record an informational message).
        // - If scheduledFor is now or in the past, mark the job as RUNNING and set runAt to current time (UTC).
        // - On parse errors, mark job as FAILED and set errorMessage.

        String scheduledForStr = entity.getScheduledFor();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (scheduledForStr == null || scheduledForStr.isBlank()) {
            logger.error("WeeklySendJob missing scheduledFor: {}", entity);
            entity.setStatus("FAILED");
            entity.setErrorMessage("Missing scheduledFor timestamp");
            return entity;
        }

        try {
            OffsetDateTime scheduledFor = OffsetDateTime.parse(scheduledForStr);

            if (scheduledFor.isAfter(now)) {
                // Scheduled time is in the future - do not start the job yet.
                logger.info("WeeklySendJob scheduled for future time ({}). Current time: {}. Job will not start now.", scheduledForStr, now.toString());
                // Keep status as CREATED and provide informational message
                entity.setErrorMessage("Scheduled time is in the future; job not started");
                return entity;
            }

            // scheduledFor is now or past -> start the job
            entity.setRunAt(now.toString());
            entity.setStatus("RUNNING");
            entity.setErrorMessage(null);
            logger.info("WeeklySendJob {} marked as RUNNING at {}", context.request().getId(), now.toString());

        } catch (DateTimeParseException e) {
            logger.error("Failed to parse scheduledFor for WeeklySendJob: {} error: {}", scheduledForStr, e.getMessage());
            entity.setStatus("FAILED");
            entity.setErrorMessage("Invalid scheduledFor format: " + e.getMessage());
        }

        return entity;
    }
}