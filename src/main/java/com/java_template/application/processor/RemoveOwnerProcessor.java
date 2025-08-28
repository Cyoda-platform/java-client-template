package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RemoveOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RemoveOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RemoveOwnerProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        try {
            // Mark owner as removed in-place (will be persisted by Cyoda automatically)
            entity.setVerificationStatus("removed");

            // Clear contact details for privacy
            entity.setContactEmail(null);
            entity.setContactPhone(null);

            // Clear saved/adopted pet references to avoid dangling references in this owner record
            entity.setSavedPets(new ArrayList<>());
            entity.setAdoptedPets(new ArrayList<>());

            // Cancel any outstanding adoption requests submitted by this owner.
            // We query AdoptionRequest entities where requesterId == ownerId
            String ownerBusinessId = entity.getOwnerId();
            if (ownerBusinessId != null && !ownerBusinessId.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.requesterId", "EQUALS", ownerBusinessId)
                );

                CompletableFuture<List<DataPayload>> filteredItemsFuture =
                    entityService.getItemsByCondition(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, condition, true);

                List<DataPayload> dataPayloads = filteredItemsFuture.get();
                if (dataPayloads != null) {
                    for (DataPayload payload : dataPayloads) {
                        try {
                            // Convert payload data to AdoptionRequest object
                            Object dataNodeObj = payload.getData();
                            JsonNode node = null;
                            if (dataNodeObj instanceof JsonNode) {
                                node = (JsonNode) dataNodeObj;
                            }

                            AdoptionRequest ar = null;
                            if (node != null) {
                                ar = objectMapper.treeToValue(node, AdoptionRequest.class);
                            } else {
                                // Fallback: try mapping directly from payload.getData() if it's not a JsonNode
                                ar = objectMapper.convertValue(dataNodeObj, AdoptionRequest.class);
                            }

                            if (ar == null) {
                                logger.warn("Unable to deserialize AdoptionRequest payload for owner {}; skipping", ownerBusinessId);
                                continue;
                            }

                            String status = ar.getStatus();
                            // Only cancel requests that are not already finalized
                            if (status == null ||
                                !(status.equalsIgnoreCase("rejected")
                                  || status.equalsIgnoreCase("completed")
                                  || status.equalsIgnoreCase("cancelled"))) {

                                ar.setStatus("cancelled");
                                ar.setDecisionAt(Instant.now().toString());

                                // Attempt to obtain technical id from the JSON node (preferred) without calling payload.getId()
                                String technicalId = null;
                                if (node != null) {
                                    if (node.has("technicalId") && !node.get("technicalId").isNull()) technicalId = node.get("technicalId").asText(null);
                                    if ((technicalId == null || technicalId.isBlank()) && node.has("technical_id") && !node.get("technical_id").isNull()) technicalId = node.get("technical_id").asText(null);
                                    if ((technicalId == null || technicalId.isBlank()) && node.has("id") && !node.get("id").isNull()) technicalId = node.get("id").asText(null);
                                    if ((technicalId == null || technicalId.isBlank()) && node.has("requestId") && !node.get("requestId").isNull()) {
                                        // requestId is business id; not a UUID technical id but we attempt to use it only if it looks like a UUID
                                        String requestIdVal = node.get("requestId").asText(null);
                                        if (requestIdVal != null && requestIdVal.matches("^[0-9a-fA-F\\-]{36}$")) technicalId = requestIdVal;
                                    }
                                }

                                if (technicalId != null && !technicalId.isBlank()) {
                                    try {
                                        entityService.updateItem(UUID.fromString(technicalId), ar).get();
                                    } catch (Exception ex) {
                                        // Log and continue processing other requests
                                        logger.error("Failed to update AdoptionRequest (technicalId={}): {}", technicalId, ex.getMessage(), ex);
                                    }
                                } else {
                                    // Cannot determine technical id - skip update and just log. Persisted update will be skipped.
                                    logger.warn("Skipping update for AdoptionRequest because technical id could not be determined for requestId={}", ar.getRequestId());
                                }
                            }
                        } catch (Exception ex) {
                            logger.error("Failed to process adoption request payload for owner {}: {}", ownerBusinessId, ex.getMessage(), ex);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error while executing RemoveOwnerProcessor logic for owner {}: {}", entity != null ? entity.getOwnerId() : "unknown", e.getMessage(), e);
            // Do not throw - return entity as-is; errors logged will surface in monitoring/observability.
        }

        return entity;
    }
}