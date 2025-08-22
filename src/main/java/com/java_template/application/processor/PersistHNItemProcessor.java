package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.hn_item.version_1.HN_Item;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistHNItemProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
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
            .validate(this::isValidEntity, "Invalid import job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        if (entity == null) return false;
        // basic check: payload must be present to attempt persisting HN item
        if (entity.getPayload() == null || entity.getPayload().isBlank()) return false;
        return true;
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();

        try {
            JsonNode payloadNode = objectMapper.readTree(job.getPayload());

            // Validate presence of required fields "id" and "type"
            List<String> missing = new ArrayList<>();
            if (!payloadNode.hasNonNull("id")) missing.add("id");
            if (!payloadNode.hasNonNull("type")) missing.add("type");

            if (!missing.isEmpty()) {
                String msg = "Missing required fields: " + String.join(",", missing);
                logger.warn("ImportJob payload validation failed: {}", msg);
                job.setStatus("FAILED");
                job.setErrorMessage(msg);
                return job;
            }

            // Ensure importTimestamp exists; if not, enrich it
            if (!payloadNode.hasNonNull("importTimestamp") || payloadNode.get("importTimestamp").asText().isBlank()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) payloadNode).put("importTimestamp", Instant.now().toString());
                // update job payload string so the enriched payload is preserved
                String updatedPayload = objectMapper.writeValueAsString(payloadNode);
                job.setPayload(updatedPayload);
            }

            // Build HN_Item entity from payload
            HN_Item hnItem = new HN_Item();
            hnItem.setRawJson(job.getPayload());
            hnItem.setId(payloadNode.get("id").asLong());
            hnItem.setType(payloadNode.get("type").asText());
            hnItem.setImportTimestamp(payloadNode.get("importTimestamp").asText());

            // Persist HN_Item as a separate entity (allowed)
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                HN_Item.ENTITY_NAME,
                String.valueOf(HN_Item.ENTITY_VERSION),
                hnItem
            );

            // We don't need the returned technical UUID for the ImportJob.resultItemId.
            // The business requirement expects the HN numeric id to be recorded.
            addFuture.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist HN_Item for import job: {}", ex.getMessage(), ex);
                } else {
                    logger.info("Persisted HN_Item technical id: {}", uuid);
                }
            });

            // Mark job as completed and record the source HN id
            job.setStatus("COMPLETED");
            job.setResultItemId(hnItem.getId());

        } catch (Exception e) {
            logger.error("Exception while persisting HN item: {}", e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage("Persist error: " + e.getMessage());
        }

        return job;
    }
}