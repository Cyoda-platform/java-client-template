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
public class RetryPolicyCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicyCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final int MAX_ATTEMPTS = 3; // configurable retry limit

    public RetryPolicyCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetryPolicyCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportTask.class)
            .validate(this::isValidEntity, "Invalid ImportTask for retry policy")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportTask entity) {
        return entity != null;
    }

    private ImportTask processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportTask> context) {
        ImportTask task = context.entity();
        try {
            Integer attempts = task.getAttemptNumber() == null ? 0 : task.getAttemptNumber();
            if (attempts < MAX_ATTEMPTS) {
                task.setStatus("RETRY_WAIT");
                // attemptedAt left as-is; will be updated when retry happens

                ObjectNode node = objectMapper.valueToTree(task);
                CompletableFuture<java.util.UUID> persist = entityService.addItem(
                    ImportTask.ENTITY_NAME,
                    String.valueOf(ImportTask.ENTITY_VERSION),
                    node
                );
                persist.whenComplete((uuid, ex) -> {
                    if (ex != null) logger.error("Failed to persist ImportTask {} to RETRY_WAIT: {}", task.getTechnicalId(), ex.getMessage());
                    else logger.info("ImportTask {} moved to RETRY_WAIT", uuid);
                });
            } else {
                // Exceeded attempts - leave as FAILED/terminal. Optionally mark for manual abort.
                logger.info("ImportTask {} exceeded max attempts ({})", task.getTechnicalId(), MAX_ATTEMPTS);
            }
        } catch (Exception e) {
            logger.error("Error applying retry policy for ImportTask {}: {}", task.getTechnicalId(), e.getMessage(), e);
        }
        return task;
    }
}
