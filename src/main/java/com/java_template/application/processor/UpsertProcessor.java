package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Iterator;
import java.util.Map;

@Component
public class UpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UpsertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        if (entity == null) return null;

        try {
            // Work at JSON/node level to avoid direct compile-time getter/setter references on the entity class.
            ObjectNode incomingNode = (ObjectNode) objectMapper.valueToTree(entity);

            // Update lastSeenAt for this ingestion
            incomingNode.put("lastSeenAt", Instant.now().toString());

            // Ensure validationStatus has a sensible default if not present
            JsonNode vsNode = incomingNode.get("validationStatus");
            if (vsNode == null || vsNode.isNull() || vsNode.asText().isBlank()) {
                incomingNode.put("validationStatus", "VALID");
            }

            // Extract source id value from incoming node (use "id" field as per entity definition)
            String sourceId = null;
            JsonNode idNode = incomingNode.get("id");
            if (idNode != null && !idNode.isNull()) {
                // treat as text for search condition
                sourceId = idNode.asText();
            }

            if (sourceId == null || sourceId.isBlank()) {
                logger.warn("Laureate entity missing source id, skipping upsert lookup; entity will be persisted as-is by workflow");
                // Return mapped Laureate back for persistence by workflow
                return objectMapper.treeToValue(incomingNode, Laureate.class);
            }

            // Build search condition to find existing laureate by source id
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.id", "EQUALS", sourceId)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                // No existing record found -> new record.
                logger.info("No existing Laureate found for source id={}, will be persisted by workflow", sourceId);
                // incomingNode already has lastSeenAt and validationStatus set above
            } else {
                // Existing record(s) found -> merge incoming non-null values into stored entity and update via EntityService.
                DataPayload existingPayload = dataPayloads.get(0);

                // Attempt to obtain the stored data JSON for the existing payload.
                JsonNode payloadTree = objectMapper.valueToTree(existingPayload);
                JsonNode existingDataNode = null;
                if (payloadTree != null && payloadTree.has("data")) {
                    existingDataNode = payloadTree.get("data").deepCopy();
                } else {
                    // As a fallback, try to serialize the DataPayload via getData() if present at runtime
                    try {
                        JsonNode maybeData = objectMapper.valueToTree(existingPayload).get("data");
                        existingDataNode = maybeData != null ? maybeData.deepCopy() : null;
                    } catch (Exception ex) {
                        existingDataNode = null;
                    }
                }

                if (existingDataNode == null || existingDataNode.isNull() || !existingDataNode.isObject()) {
                    logger.warn("Could not extract existing laureate 'data' node for source id={}, skipping update", sourceId);
                } else {
                    ObjectNode mergedNode = (ObjectNode) existingDataNode;

                    // Merge: incoming non-null fields overwrite existing ones.
                    Iterator<Map.Entry<String, JsonNode>> fields = incomingNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> f = fields.next();
                        String fname = f.getKey();
                        JsonNode fval = f.getValue();
                        if (fval != null && !fval.isNull()) {
                            mergedNode.set(fname, fval);
                        }
                    }

                    // Always update lastSeenAt on stored entity
                    mergedNode.put("lastSeenAt", Instant.now().toString());

                    // Determine technical id for the existing payload by inspecting serialized payload JSON.
                    String technicalIdStr = null;
                    if (payloadTree.has("metadata") && payloadTree.get("metadata").has("id")) {
                        JsonNode mid = payloadTree.get("metadata").get("id");
                        if (mid != null && !mid.isNull()) technicalIdStr = mid.asText();
                    }
                    if ((technicalIdStr == null || technicalIdStr.isBlank()) && payloadTree.has("id")) {
                        JsonNode topId = payloadTree.get("id");
                        if (topId != null && !topId.isNull()) technicalIdStr = topId.asText();
                    }

                    if (technicalIdStr != null && !technicalIdStr.isBlank()) {
                        try {
                            Laureate updated = objectMapper.treeToValue(mergedNode, Laureate.class);
                            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                                UUID.fromString(technicalIdStr),
                                updated
                            );
                            UUID updatedId = updatedIdFuture.get();
                            logger.info("Updated existing Laureate (technicalId={}) for source id={}", updatedId, sourceId);
                        } catch (Exception e) {
                            logger.error("Failed to update existing Laureate for source id={}: {}", sourceId, e.getMessage(), e);
                        }
                    } else {
                        logger.warn("Could not determine technical id for existing Laureate payload; skipping update for source id={}", sourceId);
                    }

                    // Ensure incoming entity reflects the validationStatus (prefer incoming if present)
                    JsonNode incomingVS = incomingNode.get("validationStatus");
                    if (incomingVS == null || incomingVS.isNull() || incomingVS.asText().isBlank()) {
                        // preserve existing validationStatus if present on stored entity
                        JsonNode existingVS = mergedNode.get("validationStatus");
                        if (existingVS != null && !existingVS.isNull() && !existingVS.asText().isBlank()) {
                            incomingNode.put("validationStatus", existingVS.asText());
                        } else {
                            incomingNode.put("validationStatus", "VALID");
                        }
                    }
                }
            }

            // Convert the incoming node back to Laureate for the workflow persistence
            Laureate resultEntity = objectMapper.treeToValue(incomingNode, Laureate.class);
            return resultEntity;

        } catch (Exception e) {
            logger.error("Error during upsert processing for Laureate: {}", e.getMessage(), e);
            try {
                // attempt to mark entity as invalid in a non-invasive way using JSON -> Laureate
                ObjectNode incomingNode = (ObjectNode) objectMapper.valueToTree(entity);
                String prev = null;
                JsonNode prevNode = incomingNode.get("validationStatus");
                if (prevNode != null && !prevNode.isNull()) prev = prevNode.asText();
                incomingNode.put("validationStatus", "INVALID:upsert_error" + (prev != null ? ";" + prev : ""));
                return objectMapper.treeToValue(incomingNode, Laureate.class);
            } catch (Exception ex) {
                // last resort: return original entity unchanged
                return entity;
            }
        }
    }
}