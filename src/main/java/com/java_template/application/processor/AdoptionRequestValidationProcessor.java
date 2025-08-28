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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class AdoptionRequestValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptionRequestValidationProcessor(SerializerFactory serializerFactory,
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

        // Business rules to implement:
        // 1. SingleActiveRequestCriterion: ensure there is no other active request for same petId and requesterId.
        //    Active statuses that block a new request: "submitted", "under_review" (case-insensitive).
        // 2. PetAvailabilityCriterion: associated Pet must exist and have status "available" (case-insensitive).
        // If any check fails -> set request.status = "rejected" and set processedAt timestamp.
        // If checks pass -> set request.status = "under_review" (move to manual review).

        // Defensive: ensure submittedAt is present (entity.isValid checked submittedAt non-blank)
        try {
            // 1) Check for other requests with same petId and requesterId
            SearchConditionRequest sameRequesterCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", entity.getPetId()),
                    Condition.of("$.requesterId", "EQUALS", entity.getRequesterId())
            );

            CompletableFuture<List<DataPayload>> requestsFuture = entityService.getItemsByCondition(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    sameRequesterCondition,
                    true
            );

            List<DataPayload> requestPayloads = requestsFuture.get();
            if (requestPayloads != null) {
                for (DataPayload payload : requestPayloads) {
                    JsonNode data = payload.getData();
                    if (data == null) continue;
                    AdoptionRequest existing;
                    try {
                        existing = objectMapper.treeToValue(data, AdoptionRequest.class);
                    } catch (Exception ex) {
                        logger.warn("Failed to deserialize existing adoption request payload: {}", ex.getMessage());
                        continue;
                    }
                    if (existing == null || existing.getId() == null) continue;
                    // skip the same entity (if re-processing the same record)
                    if (existing.getId().equals(entity.getId())) continue;
                    String st = existing.getStatus();
                    if (st == null) continue;
                    String stLower = st.toLowerCase();
                    if ("submitted".equals(stLower) || "under_review".equals(stLower)) {
                        // Violation of SingleActiveRequestCriterion
                        logger.info("Found existing active request (id={}) for petId={} requesterId={}. Marking current request as rejected.",
                                existing.getId(), entity.getPetId(), entity.getRequesterId());
                        entity.setStatus("rejected");
                        entity.setProcessedAt(Instant.now().toString());
                        return entity;
                    }
                }
            }

            // 2) Check Pet availability
            SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", entity.getPetId())
            );
            CompletableFuture<List<DataPayload>> petsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petCondition,
                    true
            );

            List<DataPayload> petPayloads = petsFuture.get();
            if (petPayloads == null || petPayloads.isEmpty()) {
                logger.info("Pet not found for petId={} - rejecting adoption request {}", entity.getPetId(), entity.getId());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            // Take first matching pet
            JsonNode petNode = petPayloads.get(0).getData();
            Pet pet = null;
            try {
                pet = objectMapper.treeToValue(petNode, Pet.class);
            } catch (Exception ex) {
                logger.error("Failed to deserialize Pet data for petId={}: {}", entity.getPetId(), ex.getMessage());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase("available")) {
                logger.info("Pet (id={}) is not available (status={}) - rejecting adoption request {}", pet.getId(), pet.getStatus(), entity.getId());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            // All criteria passed -> move to under_review
            entity.setStatus("under_review");
            // processedAt should remain null until review completes; keep processedAt as-is
            logger.info("AdoptionRequest {} moved to under_review for petId={} requesterId={}", entity.getId(), entity.getPetId(), entity.getRequesterId());
            return entity;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while validating adoption request {}: {}", entity.getId(), ie.getMessage(), ie);
            entity.setStatus("rejected");
            entity.setProcessedAt(Instant.now().toString());
            return entity;
        } catch (ExecutionException ee) {
            logger.error("Execution error while validating adoption request {}: {}", entity.getId(), ee.getMessage(), ee);
            entity.setStatus("rejected");
            entity.setProcessedAt(Instant.now().toString());
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error while validating adoption request {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setStatus("rejected");
            entity.setProcessedAt(Instant.now().toString());
            return entity;
        }
    }
}