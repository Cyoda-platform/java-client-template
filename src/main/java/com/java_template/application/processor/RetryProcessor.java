package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importtask.version_1.ImportTask;
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
public class RetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RetryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetryProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportTask.class)
            .validate(this::isValidEntity, "Invalid ImportTask for retry processor")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportTask entity) {
        return entity != null && entity.getJobTechnicalId() != null && !entity.getJobTechnicalId().isBlank();
    }

    private ImportTask processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportTask> context) {
        ImportTask task = context.entity();
        try {
            task.setStatus("IN_PROGRESS");
            task.setAttemptNumber(task.getAttemptNumber() == null ? 1 : task.getAttemptNumber() + 1);
            task.setAttemptedAt(Instant.now().toString());

            ObjectNode node = objectMapper.valueToTree(task);
            CompletableFuture<java.util.UUID> persist = entityService.addItem(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                node
            );
            persist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist ImportTask {} during retry: {}", task.getTechnicalId(), ex.getMessage());
                else logger.info("RetryProcessor persisted ImportTask {} status=IN_PROGRESS", uuid);
            });

        } catch (Exception e) {
            logger.error("Error retrying ImportTask {}: {}", task.getTechnicalId(), e.getMessage(), e);
        }
        return task;
    }
}
