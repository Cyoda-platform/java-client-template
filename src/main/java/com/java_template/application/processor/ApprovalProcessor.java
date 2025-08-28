package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            // Work with concrete AdoptionRequest entity type (implements CyodaEntity)
            .toEntity(AdoptionRequest.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidAdoptionRequest, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidAdoptionRequest(AdoptionRequest request) {
        return request != null && request.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();
        ObjectNode node = objectMapper.valueToTree(entity);

        try {
            String currentStatus = getText(node, "status");
            if (currentStatus == null || currentStatus.isBlank()) {
                logger.warn("AdoptionRequest {} has null status; rejecting", getText(node, "requestId"));
                node.put("status", "rejected");
                node.put("decisionAt", Instant.now().toString());
                if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                    node.put("reviewerId", "system");
                }
                return convertNodeToEntity(node, entity);
            }

            String statusLower = currentStatus.trim().toLowerCase();
            if (!"under_review".equalsIgnoreCase(statusLower) && !"submitted".equalsIgnoreCase(statusLower)) {
                logger.info("AdoptionRequest {} is in status '{}' and not eligible for approval; skipping state change", getText(node, "requestId"), currentStatus);
                return convertNodeToEntity(node, entity);
            }

            // Fetch Pet by petId (match external id field 'id' on Pet)
            JsonNode petNode = null;
            String petId = getText(node, "petId");
            if (petId != null && !petId.isBlank()) {
                SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", petId)
                );
                CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                    "Pet",
                    1,
                    petCondition,
                    true
                );
                List<DataPayload> petPayloads = petFuture.get();
                if (petPayloads != null && !petPayloads.isEmpty()) {
                    DataPayload payload = petPayloads.get(0);
                    if (payload != null && payload.getData() != null) {
                        petNode = payload.getData();
                    }
                }
            }

            // Fetch Owner by requesterId (match ownerId on Owner)
            JsonNode ownerNode = null;
            String requesterId = getText(node, "requesterId");
            if (requesterId != null && !requesterId.isBlank()) {
                SearchConditionRequest ownerCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.ownerId", "EQUALS", requesterId)
                );
                CompletableFuture<List<DataPayload>> ownerFuture = entityService.getItemsByCondition(
                    "Owner",
                    1,
                    ownerCondition,
                    true
                );
                List<DataPayload> ownerPayloads = ownerFuture.get();
                if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                    DataPayload payload = ownerPayloads.get(0);
                    if (payload != null && payload.getData() != null) {
                        ownerNode = payload.getData();
                    }
                }
            }

            boolean petAvailable = petNode != null && petNode.hasNonNull("status")
                && "available".equalsIgnoreCase(petNode.get("status").asText(""));
            boolean ownerVerified = ownerNode != null && ownerNode.hasNonNull("verificationStatus")
                && "verified".equalsIgnoreCase(ownerNode.get("verificationStatus").asText(""));

            if (petAvailable && ownerVerified) {
                node.put("status", "approved");
                if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                    node.put("reviewerId", "system");
                }
                node.put("decisionAt", Instant.now().toString());
                logger.info("AdoptionRequest {} approved for pet {} by requester {}", getText(node, "requestId"), petId, requesterId);
            } else {
                node.put("status", "rejected");
                if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                    node.put("reviewerId", "system");
                }
                node.put("decisionAt", Instant.now().toString());
                StringBuilder notes = new StringBuilder();
                if (!petAvailable) {
                    notes.append("Pet not available; ");
                }
                if (!ownerVerified) {
                    notes.append("Requester not verified; ");
                }
                String existingNotes = getText(node, "notes");
                String combined = (existingNotes == null ? "" : existingNotes + " ") + notes.toString().trim();
                node.put("notes", combined.isBlank() ? null : combined);
                logger.info("AdoptionRequest {} rejected: {}", getText(node, "requestId"), notes.toString().trim());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while checking eligibility for AdoptionRequest {}: {}", getText(node, "requestId"), e.getMessage(), e);
            node.put("status", "rejected");
            node.put("decisionAt", Instant.now().toString());
            if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                node.put("reviewerId", "system");
            }
            String existingNotes = getText(node, "notes");
            node.put("notes", (existingNotes == null ? "" : existingNotes + " ") + "Approval interrupted");
        } catch (ExecutionException e) {
            logger.error("Execution error while checking eligibility for AdoptionRequest {}: {}", getText(node, "requestId"), e.getMessage(), e);
            node.put("status", "rejected");
            node.put("decisionAt", Instant.now().toString());
            if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                node.put("reviewerId", "system");
            }
            String existingNotes = getText(node, "notes");
            node.put("notes", (existingNotes == null ? "" : existingNotes + " ") + "Error during eligibility checks: " + e.getCause());
        } catch (Exception e) {
            logger.error("Unexpected error in ApprovalProcessor for AdoptionRequest {}: {}", getText(node, "requestId"), e.getMessage(), e);
            node.put("status", "rejected");
            node.put("decisionAt", Instant.now().toString());
            if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                node.put("reviewerId", "system");
            }
            String existingNotes = getText(node, "notes");
            node.put("notes", (existingNotes == null ? "" : existingNotes + " ") + "Unexpected error: " + e.getMessage());
        }

        return convertNodeToEntity(node, entity);
    }

    private AdoptionRequest convertNodeToEntity(ObjectNode node, AdoptionRequest fallback) {
        try {
            return objectMapper.treeToValue(node, AdoptionRequest.class);
        } catch (Exception e) {
            logger.error("Failed to convert processed node back to AdoptionRequest, applying best-effort mapping: {}", e.getMessage(), e);
            // best-effort: update some known fields on fallback and return it
            if (fallback == null) {
                fallback = new AdoptionRequest();
            }
            String status = getText(node, "status");
            if (status != null) fallback.setStatus(status);
            String reviewerId = getText(node, "reviewerId");
            if (reviewerId != null) fallback.setReviewerId(reviewerId);
            String decisionAt = getText(node, "decisionAt");
            if (decisionAt != null) fallback.setDecisionAt(decisionAt);
            String notes = getText(node, "notes");
            if (notes != null) fallback.setNotes(notes);
            return fallback;
        }
    }

    private static String getText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText(null);
    }
}