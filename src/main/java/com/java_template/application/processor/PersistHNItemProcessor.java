package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hn_item.version_1.HN_Item;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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

@Component
public class PersistHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistHNItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();

        try {
            // Parse payload JSON
            String payload = job.getPayload();
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Payload is missing");
            }
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isNull()) {
                throw new IllegalArgumentException("Payload JSON is invalid");
            }

            // Ensure id field exists and convert to Long
            JsonNode idNode = root.get("id");
            if (idNode == null || idNode.isNull()) {
                throw new IllegalArgumentException("Missing required field 'id' in payload");
            }
            long hnId = idNode.asLong();
            if (hnId <= 0) {
                throw new IllegalArgumentException("Invalid 'id' value in payload");
            }

            // Ensure type field exists
            JsonNode typeNode = root.get("type");
            if (typeNode == null || typeNode.isNull() || typeNode.asText().isBlank()) {
                throw new IllegalArgumentException("Missing required field 'type' in payload");
            }
            String type = typeNode.asText();

            // Ensure importTimestamp exists; if not, add it
            String importTs;
            JsonNode tsNode = root.get("importTimestamp");
            if (tsNode == null || tsNode.isNull() || tsNode.asText().isBlank()) {
                importTs = Instant.now().toString();
                if (root instanceof ObjectNode) {
                    ((ObjectNode) root).put("importTimestamp", importTs);
                    // update job payload so it gets persisted with timestamp
                    job.setPayload(objectMapper.writeValueAsString(root));
                } else {
                    // fallback - set importTs but cannot modify original structure
                    importTs = Instant.now().toString();
                }
            } else {
                importTs = tsNode.asText();
            }

            // Build HN_Item entity
            HN_Item hnItem = new HN_Item();
            hnItem.setId(hnId);
            hnItem.setType(type);
            hnItem.setImportTimestamp(importTs);
            // Ensure rawJson contains the payload including importTimestamp
            hnItem.setRawJson(job.getPayload());

            // Persist HN_Item via EntityService (allowed to add other entities)
            CompletableFuture<UUID> addFuture = entityService.addItem(
                HN_Item.ENTITY_NAME,
                String.valueOf(HN_Item.ENTITY_VERSION),
                hnItem
            );

            // Wait for completion and update ImportJob accordingly
            try {
                UUID createdId = addFuture.get();
                // On success, mark job completed and attach resultItemId (HN id)
                job.setStatus("COMPLETED");
                job.setResultItemId(hnItem.getId());
                job.setErrorMessage(null);
                logger.info("Persisted HN_Item (hnId={}), created technicalId={}", hnItem.getId(), createdId);
            } catch (Exception ex) {
                // On failure, mark job failed and record error message
                job.setStatus("FAILED");
                job.setErrorMessage(ex.getMessage() == null ? "Unknown error while persisting HN_Item" : ex.getMessage());
                logger.error("Failed to persist HN_Item for job: {}, error: {}", context.request().getId(), ex.getMessage(), ex);
            }

        } catch (Exception e) {
            // If any processing/parsing error occurs, mark job failed
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() == null ? "Processing error" : e.getMessage());
            logger.error("Error processing ImportJob {}: {}", context.request().getId(), e.getMessage(), e);
        }

        return job;
    }
}