package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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
            // Work with raw JSON node to avoid relying on generated getters/setters at compile-time.
            .toEntity(ObjectNode.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntityNode, "Invalid entity state")
            .map(this::processEntityLogicNode)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityNode(ObjectNode node) {
        if (node == null) return false;
        // Required: requestId, petId, requesterId, status, submittedAt
        String requestId = getText(node, "requestId");
        String petId = getText(node, "petId");
        String requesterId = getText(node, "requesterId");
        String status = getText(node, "status");
        String submittedAt = getText(node, "submittedAt");

        return requestId != null && !requestId.isBlank()
            && petId != null && !petId.isBlank()
            && requesterId != null && !requesterId.isBlank()
            && status != null && !status.isBlank()
            && submittedAt != null && !submittedAt.isBlank();
    }

    private ObjectNode processEntityLogicNode(ProcessorSerializer.ProcessorEntityExecutionContext<ObjectNode> context) {
        ObjectNode node = context.entity();

        try {
            String currentStatus = getText(node, "status");
            if (currentStatus == null || currentStatus.isBlank()) {
                logger.warn("AdoptionRequest {} has null status; rejecting", getText(node, "requestId"));
                node.put("status", "rejected");
                node.put("decisionAt", Instant.now().toString());
                if (getText(node, "reviewerId") == null || getText(node, "reviewerId").isBlank()) {
                    node.put("reviewerId", "system");
                }
                return node;
            }

            String statusLower = currentStatus.trim().toLowerCase();
            if (!"under_review".equalsIgnoreCase(statusLower) && !"submitted".equalsIgnoreCase(statusLower)) {
                logger.info("AdoptionRequest {} is in status '{}' and not eligible for approval; skipping state change", getText(node, "requestId"), currentStatus);
                return node;
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

        return node;
    }

    private static String getText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText(null);
    }
}