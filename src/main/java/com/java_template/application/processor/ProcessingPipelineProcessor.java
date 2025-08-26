package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class ProcessingPipelineProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingPipelineProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public ProcessingPipelineProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        if (entity == null) return null;

        try {
            // Ensure attempt count is initialized and increment
            Integer attempts = entity.getAttemptCount() != null ? entity.getAttemptCount() + 1 : 1;
            entity.setAttemptCount(attempts);

            // If the job was pending, mark as running and set startedAt
            String status = entity.getStatus() != null ? entity.getStatus().toUpperCase() : "";
            if ("PENDING".equals(status)) {
                entity.setStartedAt(Instant.now().toString());
                entity.setStatus("RUNNING");
            }

            String type = entity.getType() != null ? entity.getType().toUpperCase() : "";

            switch (type) {
                case "INGEST":
                    // For INGEST jobs: enqueue a TRANSFORM job that will process the fetched payload.
                    Job transform = new Job();
                    transform.setType("TRANSFORM");
                    transform.setStatus("PENDING");
                    transform.setAttemptCount(0);
                    transform.setCreatedAt(Instant.now().toString());
                    // Propagate ingest payload details (apiUrl/rows) to the transform job if present
                    if (entity.getPayload() != null) {
                        Job.Payload p = new Job.Payload();
                        p.setApiUrl(entity.getPayload().getApiUrl());
                        p.setRows(entity.getPayload().getRows());
                        transform.setPayload(p);
                    }
                    try {
                        UUID created = entityService.addItem(
                            Job.ENTITY_NAME,
                            String.valueOf(Job.ENTITY_VERSION),
                            transform
                        ).get();
                        logger.info("Created TRANSFORM job with id {}", created);
                        // Link created job reference to the ingest job resultRef
                        entity.setResultRef(created != null ? created.toString() : null);
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.error("Failed to create TRANSFORM job from INGEST job", ex);
                        entity.setStatus("FAILED");
                        entity.setCompletedAt(Instant.now().toString());
                    }
                    break;

                case "TRANSFORM":
                    // For TRANSFORM jobs: create a NOTIFY job to dispatch results to subscribers
                    Job notify = new Job();
                    notify.setType("NOTIFY");
                    notify.setStatus("PENDING");
                    notify.setAttemptCount(0);
                    notify.setCreatedAt(Instant.now().toString());
                    // Propagate any payload reference
                    if (entity.getPayload() != null) {
                        Job.Payload p2 = new Job.Payload();
                        p2.setApiUrl(entity.getPayload().getApiUrl());
                        p2.setRows(entity.getPayload().getRows());
                        notify.setPayload(p2);
                    }
                    try {
                        UUID createdNotify = entityService.addItem(
                            Job.ENTITY_NAME,
                            String.valueOf(Job.ENTITY_VERSION),
                            notify
                        ).get();
                        logger.info("Created NOTIFY job with id {}", createdNotify);
                        entity.setResultRef(createdNotify != null ? createdNotify.toString() : null);
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.error("Failed to create NOTIFY job from TRANSFORM job", ex);
                        entity.setStatus("FAILED");
                        entity.setCompletedAt(Instant.now().toString());
                    }
                    break;

                case "NOTIFY":
                    // For NOTIFY jobs: mark as completed (actual notification dispatch handled by downstream processors/services)
                    entity.setCompletedAt(Instant.now().toString());
                    entity.setStatus("COMPLETED");
                    break;

                default:
                    logger.warn("Unknown job type '{}', marking as FAILED", entity.getType());
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while processing job", ex);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
        }

        return entity;
    }
}