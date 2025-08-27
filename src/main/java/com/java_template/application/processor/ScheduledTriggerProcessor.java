package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ScheduledTriggerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTriggerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ScheduledTriggerProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyJob> context) {
        WeeklyJob entity = context.entity();

        // Mark the job as RUNNING and set lastRunAt to now.
        entity.setStatus("RUNNING");
        String now = Instant.now().toString();
        entity.setLastRunAt(now);

        // Log the trigger action.
        logger.info("WeeklyJob triggered: id={}, name={}, apiEndpoint={}", entity.getId(), entity.getName(), entity.getApiEndpoint());

        // Create a lightweight ingestion event record for downstream processors/consumers.
        // We don't modify the triggering entity via EntityService (Cyoda will persist the changed entity automatically).
        // Use EntityService to add an IngestionEvent so other workflows can react to it.
        try {
            ObjectNode ingestion = objectMapper.createObjectNode();
            ingestion.put("jobId", entity.getId());
            ingestion.put("jobName", entity.getName());
            ingestion.put("source", entity.getApiEndpoint());
            ingestion.put("createdAt", now);
            ingestion.put("runTime", entity.getRunTime() != null ? entity.getRunTime() : "");
            ingestion.put("recurrenceDay", entity.getRecurrenceDay() != null ? entity.getRecurrenceDay() : "");
            // Fire-and-forget: persist ingestion event as a separate entity model "IngestionEvent" version "1"
            CompletableFuture<Object> idFuture = entityService.addItem(
                "IngestionEvent",
                "1",
                ingestion
            );
            idFuture.whenComplete((res, ex) -> {
                if (ex != null) {
                    logger.error("Failed to create IngestionEvent for jobId={}: {}", entity.getId(), ex.getMessage());
                } else {
                    logger.info("Created IngestionEvent for jobId={}, response={}", entity.getId(), res);
                }
            });
        } catch (Exception ex) {
            logger.error("Exception while creating ingestion event for WeeklyJob id={}: {}", entity.getId(), ex.getMessage());
            // Do not throw; mark as RUNNING and allow downstream error handling to occur.
        }

        return entity;
    }
}