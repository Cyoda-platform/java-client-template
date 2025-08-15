package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryHnItemStore;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ProcessImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final InMemoryHnItemStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessImportProcessor(SerializerFactory serializerFactory, InMemoryHnItemStore store) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.store = store;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing import for HackerNewsItem request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        if (entity == null) return null;

        // Ensure importTimestamp is set or updated (epoch millis truncated to seconds)
        long importMillis = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
        try {
            entity.setImportTimestamp(importMillis);
        } catch (Exception e) {
            logger.warn("Failed to set importTimestamp on entity: {}", e.getMessage());
        }

        // Enrich originalJson: preserve all original fields and add/update importTimestamp as ISO-8601 string
        String orig = entity.getOriginalJson();
        if (orig != null && !orig.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(orig);
                if (root.isObject()) {
                    ObjectNode obj = (ObjectNode) root;
                    String isoTs = Instant.ofEpochMilli(importMillis).truncatedTo(ChronoUnit.SECONDS).toString();
                    obj.put("importTimestamp", isoTs);
                    String updated = objectMapper.writeValueAsString(obj);
                    entity.setOriginalJson(updated);
                } else {
                    // If originalJson is not an object, synthesize minimal object
                    ObjectNode obj = objectMapper.createObjectNode();
                    obj.put("id", entity.getId());
                    obj.put("type", entity.getType() == null ? "" : entity.getType());
                    obj.put("importTimestamp", Instant.ofEpochMilli(importMillis).truncatedTo(ChronoUnit.SECONDS).toString());
                    entity.setOriginalJson(objectMapper.writeValueAsString(obj));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse/enrich originalJson, falling back to synthesized JSON: {}", e.getMessage());
                try {
                    ObjectNode obj = objectMapper.createObjectNode();
                    obj.put("id", entity.getId());
                    obj.put("type", entity.getType() == null ? "" : entity.getType());
                    obj.put("importTimestamp", Instant.ofEpochMilli(importMillis).truncatedTo(ChronoUnit.SECONDS).toString());
                    entity.setOriginalJson(objectMapper.writeValueAsString(obj));
                } catch (Exception ex) {
                    logger.error("Failed to synthesize originalJson: {}", ex.getMessage());
                }
            }
        } else {
            // No original JSON provided - synthesize a minimal JSON record with importTimestamp
            try {
                ObjectNode obj = objectMapper.createObjectNode();
                obj.put("id", entity.getId());
                obj.put("type", entity.getType() == null ? "" : entity.getType());
                obj.put("importTimestamp", Instant.ofEpochMilli(importMillis).truncatedTo(ChronoUnit.SECONDS).toString());
                entity.setOriginalJson(objectMapper.writeValueAsString(obj));
            } catch (Exception e) {
                logger.error("Failed to synthesize originalJson for entity id={}: {}", entity.getId(), e.getMessage());
            }
        }

        // Persist
        try {
            boolean created = store.upsert(entity);
            logger.info("Processed import for id={} created={}", entity.getId(), created);
        } catch (Exception e) {
            logger.error("Failed to persist HackerNewsItem id={}: {}", entity.getId(), e.getMessage());
        }
        return entity;
    }
}
