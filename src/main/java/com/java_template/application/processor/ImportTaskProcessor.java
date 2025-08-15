package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.importtask.version_1.ImportTask;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ImportTaskProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportTaskProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int MAX_RETRIES = 3;

    public ImportTaskProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportTask for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportTask.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportTask task) {
        return task != null && task.getJobTechnicalId() != null && !task.getJobTechnicalId().isBlank();
    }

    private ImportTask processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportTask> context) {
        ImportTask task = context.entity();

        // Mark processing
        task.setStatus("PROCESSING");
        task.setLastUpdatedAt(Instant.now());
        task.setAttempts((task.getAttempts() == null ? 0 : task.getAttempts()) + 1);

        // Load HackerNewsItem by hnItemId if present
        HackerNewsItem item = null;
        if (task.getHnItemId() != null) {
            try {
                // Search by hn item id
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> searchFuture = entityService.getItemsByCondition(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.id", "EQUALS", String.valueOf(task.getHnItemId()))),
                    true
                );
                ArrayNode arr = searchFuture.get(10, TimeUnit.SECONDS);
                if (arr != null && arr.size() > 0) {
                    ObjectNode stored = (ObjectNode) arr.get(0);
                    if (stored.has("originalJson")) {
                        String original = stored.get("originalJson").asText();
                        item = mapper.readValue(original, HackerNewsItem.class);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to load HackerNewsItem by hnItemId {}: {}", task.getHnItemId(), e.getMessage());
            }
        }

        // If no item, try to load by jobTechnicalId mapping (e.g., find item whose originalJson equals job payload)
        if (item == null) {
            try {
                // Heuristic: find first HackerNewsItem where originalJson contains jobTechnicalId
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> searchFuture = entityService.getItemsByCondition(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.originalJson", "IEQUALS", "")),
                    true
                );
                ArrayNode arr = searchFuture.get(10, TimeUnit.SECONDS);
                if (arr != null && arr.size() > 0) {
                    ObjectNode stored = (ObjectNode) arr.get(0);
                    if (stored.has("originalJson")) {
                        String original = stored.get("originalJson").asText();
                        HackerNewsItem tmp = new HackerNewsItem();
                        tmp.setOriginalJson(original);
                        item = tmp; // minimal reconstruction
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to heuristic load HackerNewsItem for task {}: {}", task.getJobTechnicalId(), e.getMessage());
            }
        }

        if (item == null) {
            task.setStatus("FAILED");
            task.setErrorMessage("HackerNewsItem not found");
            task.setLastUpdatedAt(Instant.now());
            return task;
        }

        // Run validation criterion (use existing criterion logic by inline checks)
        boolean valid = false;
        try {
            JsonNode node = mapper.readTree(item.getOriginalJson());
            boolean hasId = node.has("id");
            boolean hasType = node.has("type");
            if (hasId && hasType) {
                valid = true;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse originalJson during task processing: {}", e.getMessage());
        }

        // Enrichment
        try {
            JsonNode node = mapper.readTree(item.getOriginalJson());
            if (node.has("id") && node.get("id").canConvertToLong()) {
                item.setId(node.get("id").longValue());
            }
            if (node.has("type") && !node.get("type").isNull()) {
                item.setType(node.get("type").asText());
            }
            item.setImportTimestamp(Instant.now());
        } catch (Exception e) {
            logger.warn("Enrichment failed for task {}: {}", task.getJobTechnicalId(), e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage("Enrichment failed: " + e.getMessage());
            task.setLastUpdatedAt(Instant.now());
            return task;
        }

        // State assignment
        if (item.getId() != null && item.getType() != null && !item.getType().isBlank()) {
            item.setState("VALID");
            item.setValidationErrors(null);
            task.setStatus("SUCCEEDED");
            task.setErrorMessage(null);
        } else {
            item.setState("INVALID");
            item.setValidationErrors("missing id and/or type");
            // Do not retry invalid content - terminal failure
            task.setStatus("FAILED");
            task.setErrorMessage("Invalid HackerNewsItem: missing id or type");
        }

        // Persist updated item and task
        try {
            // Try to find existing stored record to update
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> searchFuture = entityService.getItemsByCondition(
                HackerNewsItem.ENTITY_NAME,
                String.valueOf(HackerNewsItem.ENTITY_VERSION),
                com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.originalJson", "EQUALS", item.getOriginalJson())),
                true
            );
            com.fasterxml.jackson.databind.node.ArrayNode arr = searchFuture.get(10, TimeUnit.SECONDS);
            if (arr != null && arr.size() > 0) {
                ObjectNode stored = (ObjectNode) arr.get(0);
                if (stored.has("technicalId")) {
                    UUID technical = UUID.fromString(stored.get("technicalId").asText());
                    entityService.updateItem(HackerNewsItem.ENTITY_NAME, String.valueOf(HackerNewsItem.ENTITY_VERSION), technical, item).get(10, TimeUnit.SECONDS);
                }
            } else {
                // create if not exists
                entityService.addItem(HackerNewsItem.ENTITY_NAME, String.valueOf(HackerNewsItem.ENTITY_VERSION), item).get(10, TimeUnit.SECONDS);
            }

            // Persist task
            ImportTask toPersist = new ImportTask();
            toPersist.setJobTechnicalId(task.getJobTechnicalId());
            toPersist.setHnItemId(item.getId());
            toPersist.setStatus(task.getStatus());
            toPersist.setAttempts(task.getAttempts());
            toPersist.setErrorMessage(task.getErrorMessage());
            toPersist.setCreatedAt(task.getCreatedAt());
            toPersist.setLastUpdatedAt(Instant.now());
            entityService.addItem(ImportTask.ENTITY_NAME, String.valueOf(ImportTask.ENTITY_VERSION), toPersist).get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            logger.error("Failed to persist item/task during ImportTask processing: {}", e.getMessage());
            // If persistence failed due to transient error, decide retry
            if (task.getAttempts() < MAX_RETRIES) {
                task.setStatus("QUEUED");
                task.setLastUpdatedAt(Instant.now());
            } else {
                task.setStatus("FAILED");
                task.setLastUpdatedAt(Instant.now());
                task.setErrorMessage("Persistence failed after max retries: " + e.getMessage());
            }
        }

        return task;
    }
}
