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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExecuteJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ExecuteJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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

        // mark start
        entity.setStartedAt(Instant.now().toString());

        // increment attempt count
        if (entity.getAttemptCount() == null) {
            entity.setAttemptCount(1);
        } else {
            entity.setAttemptCount(entity.getAttemptCount() + 1);
        }

        try {
            String type = entity.getType() != null ? entity.getType().trim().toUpperCase() : "";

            switch (type) {
                case "INGEST":
                    // Ingest: create a TRANSFORM job using the same payload (so downstream processors can pick it up)
                    Job.Payload payloadForTransform = entity.getPayload();
                    Job transformJob = new Job();
                    transformJob.setCreatedAt(Instant.now().toString());
                    transformJob.setType("TRANSFORM");
                    transformJob.setStatus("PENDING");
                    transformJob.setAttemptCount(0);
                    transformJob.setPayload(payloadForTransform);

                    // Add the new transform job via EntityService (other-entity add is allowed)
                    CompletableFuture<UUID> transformIdFuture = entityService.addItem(
                        Job.ENTITY_NAME,
                        String.valueOf(Job.ENTITY_VERSION),
                        transformJob
                    );
                    // try to get the resulting technical id and store as resultRef for traceability
                    try {
                        UUID transformId = transformIdFuture.get();
                        if (transformId != null) {
                            entity.setResultRef(transformId.toString());
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to obtain transform job id after addItem", ex);
                    }

                    entity.setStatus("COMPLETED");
                    entity.setCompletedAt(Instant.now().toString());
                    break;

                case "TRANSFORM":
                    // Transform: create a NOTIFY job using the same payload
                    Job.Payload payloadForNotify = entity.getPayload();
                    Job notifyJob = new Job();
                    notifyJob.setCreatedAt(Instant.now().toString());
                    notifyJob.setType("NOTIFY");
                    notifyJob.setStatus("PENDING");
                    notifyJob.setAttemptCount(0);
                    notifyJob.setPayload(payloadForNotify);

                    CompletableFuture<UUID> notifyIdFuture = entityService.addItem(
                        Job.ENTITY_NAME,
                        String.valueOf(Job.ENTITY_VERSION),
                        notifyJob
                    );
                    try {
                        UUID notifyId = notifyIdFuture.get();
                        if (notifyId != null) {
                            entity.setResultRef(notifyId.toString());
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to obtain notify job id after addItem", ex);
                    }

                    entity.setStatus("COMPLETED");
                    entity.setCompletedAt(Instant.now().toString());
                    break;

                case "NOTIFY":
                    // Notify: execution finished for orchestration job. Actual notifications are handled by downstream processors/services.
                    entity.setStatus("COMPLETED");
                    entity.setCompletedAt(Instant.now().toString());
                    break;

                default:
                    logger.warn("Unknown job type '{}', marking as FAILED", entity.getType());
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    break;
            }
        } catch (Exception ex) {
            logger.error("Exception while executing job processor", ex);
            // mark failure; retry policy is handled by workflow criteria/processors externally
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
        }

        return entity;
    }
}