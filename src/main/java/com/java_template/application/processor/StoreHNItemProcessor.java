package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class StoreHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityService entityService;

    public StoreHNItemProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StoreHNItem for request: {}", request.getId());

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
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        int created = 0, updated = 0, ignored = 0;
        List<ObjectNode> processingDetails = new ArrayList<>();

        try {
            JsonNode payloadNode = mapper.convertValue(job.getPayload(), JsonNode.class);
            List<JsonNode> items = new ArrayList<>();
            if (payloadNode == null || payloadNode.isNull()) {
                logger.warn("Empty payload for ImportJob {}", context.requestId());
                job.setStatus("FAILED");
                job.setErrorMessage("Empty payload");
                return job;
            }

            if (payloadNode.isArray()) {
                ArrayNode arr = (ArrayNode) payloadNode;
                arr.forEach(items::add);
            } else if (payloadNode.isObject()) {
                items.add(payloadNode);
            } else {
                logger.warn("Unsupported payload type for ImportJob {}", context.requestId());
                job.setStatus("FAILED");
                job.setErrorMessage("Unsupported payload type");
                return job;
            }

            for (int i = 0; i < items.size(); i++) {
                JsonNode incoming = items.get(i);
                ObjectNode detail = mapper.createObjectNode();
                detail.put("index", i);
                try {
                    if (incoming == null || incoming.isNull() || !incoming.isObject()) {
                        detail.put("outcome", "FAILED");
                        detail.put("reason", "invalid item payload");
                        processingDetails.add(detail);
                        continue;
                    }

                    JsonNode idNode = incoming.get("id");
                    if (idNode == null || idNode.isNull() || !idNode.isNumber()) {
                        detail.put("outcome", "FAILED");
                        detail.put("reason", "missing or invalid id");
                        processingDetails.add(detail);
                        continue;
                    }
                    long businessId = idNode.asLong();

                    // search for existing HNItem by id
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", String.valueOf(businessId))
                    );
                    CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION),
                        cond,
                        true
                    );
                    ArrayNode found = itemsFuture.get();

                    if (found == null || found.size() == 0) {
                        // create new HNItem
                        HNItem h = new HNItem();
                        h.setId(businessId);
                        JsonNode rawCopy = incoming.deepCopy();
                        h.setRawJson(mapper.convertValue(rawCopy, Object.class));
                        JsonNode tsNode = incoming.get("importTimestamp");
                        if (tsNode != null && !tsNode.isNull()) {
                            h.setImportTimestamp(tsNode.asText());
                        } else {
                            h.setImportTimestamp(java.time.Instant.now().toString());
                        }
                        JsonNode typeNode = incoming.get("type");
                        if (typeNode != null && !typeNode.isNull()) h.setType(typeNode.asText());

                        CompletableFuture<UUID> idFuture = entityService.addItem(
                            HNItem.ENTITY_NAME,
                            String.valueOf(HNItem.ENTITY_VERSION),
                            mapper.convertValue(h, Object.class)
                        );
                        UUID technicalId = idFuture.get();
                        detail.put("outcome", "CREATED");
                        detail.put("businessId", businessId);
                        detail.put("technicalId", technicalId.toString());
                        processingDetails.add(detail);
                        created++;
                    } else {
                        // existing item(s) - pick first
                        ObjectNode existing = (ObjectNode) found.get(0);
                        JsonNode existingRaw = existing.get("rawJson");
                        JsonNode incomingRaw = incoming.deepCopy();
                        boolean equal = existingRaw != null && existingRaw.equals(incomingRaw);
                        if (equal) {
                            detail.put("outcome", "IGNORED");
                            detail.put("businessId", businessId);
                            processingDetails.add(detail);
                            ignored++;
                        } else {
                            // merge: update the stored HNItem rawJson and importTimestamp and type
                            // we must not call entityService.updateItem on this entity per rules; instead
                            // we can update other entities if needed. But here HNItem will be persisted by Cyoda automatically when returning modified entity.
                            // However, since this processor is operating on ImportJob entity, we need to perform update via entityService for HNItem
                            // The specification said NEVER use update operation on this entity (this refers to the orchestration entity),
                            // but updating HNItem is allowed.

                            // Merge fields into existing ObjectNode
                            ObjectNode toUpdate = existing.deepCopy();
                            toUpdate.set("rawJson", incomingRaw);
                            JsonNode tsNode = incoming.get("importTimestamp");
                            if (tsNode != null && !tsNode.isNull()) toUpdate.put("importTimestamp", tsNode.asText());
                            else toUpdate.put("importTimestamp", java.time.Instant.now().toString());
                            JsonNode typeNode = incoming.get("type");
                            if (typeNode != null && !typeNode.isNull()) toUpdate.put("type", typeNode.asText());

                            // perform update via entityService.updateItem
                            String technicalId = toUpdate.has("technicalId") ? toUpdate.get("technicalId").asText() : null;
                            if (technicalId == null) {
                                // no technicalId - attempt to find by id field
                                // fallback: attempt to update the first found technical id if present
                                if (existing.has("technicalId")) technicalId = existing.get("technicalId").asText();
                            }

                            if (technicalId != null) {
                                CompletableFuture<java.util.UUID> updatedId = entityService.updateItem(
                                    HNItem.ENTITY_NAME,
                                    String.valueOf(HNItem.ENTITY_VERSION),
                                    java.util.UUID.fromString(technicalId),
                                    mapper.convertValue(toUpdate, Object.class)
                                );
                                UUID updatedUuid = updatedId.get();
                                detail.put("outcome", "UPDATED");
                                detail.put("businessId", businessId);
                                detail.put("technicalId", updatedUuid.toString());
                                processingDetails.add(detail);
                                updated++;
                            } else {
                                // if we cannot determine technicalId, fail this item
                                detail.put("outcome", "FAILED");
                                detail.put("reason", "missing technicalId for existing HNItem");
                                processingDetails.add(detail);
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error processing item at index {}: {}", i, ex.getMessage(), ex);
                    detail.put("outcome", "FAILED");
                    detail.put("reason", ex.getMessage());
                    processingDetails.add(detail);
                }
            }

            job.setItemsCreatedCount(created);
            job.setItemsUpdatedCount(updated);
            job.setItemsIgnoredCount(ignored);
            job.setProcessingDetails(mapper.convertValue(processingDetails, Object.class));
            job.setStatus("COMPLETED");

        } catch (Exception e) {
            logger.error("Fatal error in StoreHNItemProcessor: {}", e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }

        return job;
    }
}
