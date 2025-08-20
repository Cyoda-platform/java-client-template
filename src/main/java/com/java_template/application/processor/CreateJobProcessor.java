package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static com.java_template.common.config.Config.*;

@Component
public class CreateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity((Class) ObjectNode.class)
            .validate(this::isValidPayload, "Invalid create job payload")
            .map(ctx -> processEntityLogic(ctx))
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayload(ObjectNode payload) {
        return payload != null && payload.hasNonNull("type");
    }

    private ObjectNode processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<?> context) {
        ObjectNode payload = (ObjectNode) context.entity();
        try {
            Job job = new Job();
            job.setType(payload.get("type").asText());
            job.setStatus("PENDING");
            job.setRequestId(payload.hasNonNull("requestId") ? payload.get("requestId").asText() : null);
            job.setTargetEntityId(payload.hasNonNull("targetEntityId") ? payload.get("targetEntityId").asText() : null);
            job.setPayload(payload.toString());
            job.setCreatedAt(Instant.now());
            CompletableFuture<UUID> fut = entityService.addItem(Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), job);
            UUID id = fut.get();
            payload.put("jobId", id.toString());
            logger.info("Created Job {} of type {}", id, job.getType());
        } catch (Exception e) {
            logger.error("Error in CreateJobProcessor", e);
        }
        return payload;
    }
}
