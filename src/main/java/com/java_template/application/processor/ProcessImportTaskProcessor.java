package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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
public class ProcessImportTaskProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessImportTaskProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProcessImportTaskProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ProcessImportTaskProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportTask.class)
            .validate(this::isValidEntity, "Invalid ImportTask for processing")
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

            // Persist task status
            ObjectNode taskNode = objectMapper.valueToTree(task);
            CompletableFuture<java.util.UUID> taskPersist = entityService.addItem(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                taskNode
            );
            taskPersist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist ImportTask {} status update: {}", task.getTechnicalId(), ex.getMessage());
                else logger.info("Persisted ImportTask {} status IN_PROGRESS", uuid);
            });

            // Build HNItem from task.result or payload - here we expect task.result to contain the original item JSON under "payload" if set earlier
            String payload = task.getResult();
            HNItem item = new HNItem();
            if (payload != null && !payload.isBlank()) {
                JsonNode node = objectMapper.readTree(payload);
                if (node.has("id")) item.setId(node.get("id").asLong());
                if (node.has("type")) item.setType(node.get("type").asText());
                item.setOriginalJson(node.toString());
            } else {
                // No payload present; cannot process
                throw new IllegalStateException("ImportTask payload missing");
            }

            // Enrich timestamps
            String now = Instant.now().toString();
            if (item.getImportTimestamp() == null || item.getImportTimestamp().isBlank()) item.setImportTimestamp(now);
            if (item.getCreatedAt() == null || item.getCreatedAt().isBlank()) item.setCreatedAt(now);
            item.setUpdatedAt(now);

            // Validate fields
            boolean valid = true;
            try {
                JsonNode originalNode = objectMapper.readTree(item.getOriginalJson());
                if (!originalNode.has("id") || !originalNode.has("type")) valid = false;
            } catch (Exception e) {
                valid = false;
            }

            // Persist HNItem
            ObjectNode hnNode = objectMapper.valueToTree(item);
            CompletableFuture<java.util.UUID> hnPersist = entityService.addItem(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                hnNode
            );
            hnPersist.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist HNItem from task {}: {}", task.getTechnicalId(), ex.getMessage());
                } else {
                    logger.info("Persisted HNItem for task {} technicalId={}", task.getTechnicalId(), uuid);
                }
            });

            // Update task result and status based on validation
            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("persistedId", item.getId());
            task.setResult(resultNode.toString());
            task.setStatus(valid ? "SUCCEEDED" : "FAILED");

            // If invalid, we may push to review queue etc. that is handled by other processors

            // Persist final task
            ObjectNode finalTaskNode = objectMapper.valueToTree(task);
            CompletableFuture<java.util.UUID> finalPersist = entityService.addItem(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                finalTaskNode
            );
            finalPersist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist final ImportTask {}: {}", task.getTechnicalId(), ex.getMessage());
                else logger.info("Persisted final ImportTask {} status={}", uuid, task.getStatus());
            });

        } catch (Exception e) {
            logger.error("Error processing ImportTask {}: {}", task.getTechnicalId(), e.getMessage(), e);
            try {
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("error", e.getMessage());
                task.setResult(resultNode.toString());
                task.setStatus("FAILED");
                task.setAttemptedAt(Instant.now().toString());
                ObjectNode finalTaskNode = objectMapper.valueToTree(task);
                entityService.addItem(
                    ImportTask.ENTITY_NAME,
                    String.valueOf(ImportTask.ENTITY_VERSION),
                    finalTaskNode
                );
            } catch (Exception ex) {
                logger.error("Failed to persist failed ImportTask {}: {}", task.getTechnicalId(), ex.getMessage(), ex);
            }
        }

        return task;
    }
}
