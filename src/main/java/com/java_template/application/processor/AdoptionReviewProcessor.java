package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class AdoptionReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptionReviewProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Business rules:
        // - Processor is invoked when a staff decision is made (status expected to be "approved" or "rejected").
        // - On approval: mark the request as processed (processedAt, processedBy if available) and update the related Pet to "pending".
        // - On rejection: mark the request as processed (processedAt, processedBy) and leave pet unchanged.
        // - Do not modify the AdoptionRequest via entityService (the triggering entity will be persisted automatically).
        // - Only update other entities (Pet) via entityService when needed.

        try {
            String status = entity.getStatus();
            if (status != null) {
                String normalized = status.trim().toLowerCase();
                // Set processedAt timestamp for decisions
                if ("approved".equals(normalized) || "rejected".equals(normalized)) {
                    // set processedAt to now (ISO string)
                    String now = Instant.now().toString();
                    entity.setProcessedAt(now);

                    // Populate processedBy if not already set
                    if (entity.getProcessedBy() == null || entity.getProcessedBy().isBlank()) {
                        entity.setProcessedBy("manual-review");
                    }

                    if ("approved".equals(normalized)) {
                        // Update Pet status to "pending" to indicate adoption flow started.
                        // Find pet by business id (Pet.id == adoptionRequest.petId)
                        try {
                            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                                Condition.of("$.id", "EQUALS", entity.getPetId())
                            );
                            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                                Pet.ENTITY_NAME,
                                Pet.ENTITY_VERSION,
                                condition,
                                true
                            );
                            List<DataPayload> dataPayloads = itemsFuture.get();
                            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                                DataPayload payload = dataPayloads.get(0);
                                JsonNode dataNode = payload.getData();
                                JsonNode metaNode = payload.getMeta();
                                if (dataNode != null) {
                                    // convert to Pet
                                    Pet pet = objectMapper.treeToValue(dataNode, Pet.class);
                                    if (pet != null) {
                                        pet.setStatus("pending");
                                        // Attempt to extract technicalId from meta first, then from data
                                        String technicalId = null;
                                        try {
                                            if (metaNode != null && metaNode.has("technicalId") && !metaNode.get("technicalId").isNull()) {
                                                technicalId = metaNode.get("technicalId").asText();
                                            }
                                        } catch (Exception ex) {
                                            // ignore and fallback
                                        }
                                        if ((technicalId == null || technicalId.isBlank()) && dataNode != null) {
                                            try {
                                                if (dataNode.has("technicalId") && !dataNode.get("technicalId").isNull()) {
                                                    technicalId = dataNode.get("technicalId").asText();
                                                }
                                            } catch (Exception ex) {
                                                // ignore and proceed
                                            }
                                        }

                                        if (technicalId != null && !technicalId.isBlank()) {
                                            try {
                                                CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), pet);
                                                updated.get();
                                                logger.info("Updated Pet (business id={}) status to pending", pet.getId());
                                            } catch (InterruptedException | ExecutionException ex) {
                                                logger.error("Failed to update Pet status for petId {}: {}", entity.getPetId(), ex.getMessage(), ex);
                                            }
                                        } else {
                                            logger.warn("Could not determine technicalId for Pet with business id {}; Pet update skipped", entity.getPetId());
                                        }
                                    }
                                }
                            } else {
                                logger.warn("No Pet found with business id {} while processing approved AdoptionRequest {}", entity.getPetId(), entity.getId());
                            }
                        } catch (InterruptedException | ExecutionException ex) {
                            logger.error("Error while fetching Pet for AdoptionRequest {}: {}", entity.getId(), ex.getMessage(), ex);
                        }
                    } else {
                        // Rejected: nothing to update on Pet side. Logging only.
                        logger.info("AdoptionRequest {} rejected by reviewer", entity.getId());
                    }
                } else {
                    // Not a decision action; no processing needed. log and return.
                    logger.debug("AdoptionRequest {} status is '{}'; no manual decision applied", entity.getId(), status);
                }
            } else {
                logger.warn("AdoptionRequest {} has null status; skipping review processing", entity.getId());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error processing AdoptionRequest {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }
}