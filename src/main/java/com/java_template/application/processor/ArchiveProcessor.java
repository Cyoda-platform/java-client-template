package com.java_template.application.processor;
import com.java_template.application.entity.hn_item.version_1.HN_Item;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArchiveProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HN_Item for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HN_Item.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HN_Item entity) {
        return entity != null && entity.isValid();
    }

    private HN_Item processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HN_Item> context) {
        HN_Item entity = context.entity();

        // Business goal: mark the stored HN item as archived.
        // We must not call update on this entity via EntityService; instead modify the entity state so Cyoda persists it.
        // Approach: update the rawJson payload to include an "archived": true flag. Preserve existing fields where possible.

        String raw = entity.getRawJson();
        if (raw == null || raw.isBlank()) {
            // No raw JSON available — create a minimal JSON with known entity fields and archived flag
            ObjectNode created = objectMapper.createObjectNode();
            if (entity.getId() != null) created.put("id", entity.getId());
            if (entity.getType() != null) created.put("type", entity.getType());
            if (entity.getImportTimestamp() != null) created.put("importTimestamp", entity.getImportTimestamp());
            created.put("archived", true);
            try {
                entity.setRawJson(objectMapper.writeValueAsString(created));
            } catch (JsonProcessingException e) {
                // Fallback: set a simple string representation
                logger.warn("Failed to serialize fallback archived JSON for HN_Item id={}: {}", entity.getId(), e.getMessage());
                entity.setRawJson("{\"archived\":true}");
            }
            return entity;
        }

        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;
                obj.put("archived", true);
                // ensure importTimestamp exists in raw JSON (do not overwrite if present)
                if (!obj.has("importTimestamp") && entity.getImportTimestamp() != null) {
                    obj.put("importTimestamp", entity.getImportTimestamp());
                }
                entity.setRawJson(objectMapper.writeValueAsString(obj));
            } else {
                // Raw JSON is not an object (array, primitive), wrap it into an object with original payload and archived flag
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.set("original", node);
                if (entity.getId() != null) wrapper.put("id", entity.getId());
                if (entity.getType() != null) wrapper.put("type", entity.getType());
                if (entity.getImportTimestamp() != null) wrapper.put("importTimestamp", entity.getImportTimestamp());
                wrapper.put("archived", true);
                entity.setRawJson(objectMapper.writeValueAsString(wrapper));
            }
        } catch (JsonProcessingException e) {
            // If parsing fails, create a new JSON preserving known fields and mark archived
            logger.warn("Failed to parse rawJson for HN_Item id={}: {}. Creating fallback archived JSON.", entity.getId(), e.getMessage());
            ObjectNode created = objectMapper.createObjectNode();
            if (entity.getId() != null) created.put("id", entity.getId());
            if (entity.getType() != null) created.put("type", entity.getType());
            if (entity.getImportTimestamp() != null) created.put("importTimestamp", entity.getImportTimestamp());
            created.put("archived", true);
            // also preserve the original raw string under a field to avoid data loss
            created.put("originalRawJson", raw);
            try {
                entity.setRawJson(objectMapper.writeValueAsString(created));
            } catch (JsonProcessingException ex) {
                logger.error("Failed to serialize fallback archived JSON for HN_Item id={}: {}", entity.getId(), ex.getMessage());
                entity.setRawJson("{\"archived\":true}");
            }
        }

        return entity;
    }
}