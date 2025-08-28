package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping(path = "/api/v1/adoption-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AdoptionRequest", description = "APIs for AdoptionRequest entity (version 1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Create a new AdoptionRequest entity. Returns the technicalId of the persisted entity.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAdoptionRequest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAdoptionRequestRequest.class)))
            @RequestBody CreateAdoptionRequestRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic validation
            if (request.getId() == null || request.getId().isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (request.getPetId() == null || request.getPetId().isBlank()) {
                throw new IllegalArgumentException("petId is required");
            }
            if (request.getRequesterId() == null || request.getRequesterId().isBlank()) {
                throw new IllegalArgumentException("requesterId is required");
            }
            if (request.getSubmittedAt() == null || request.getSubmittedAt().isBlank()) {
                throw new IllegalArgumentException("submittedAt is required");
            }

            AdoptionRequest entity = new AdoptionRequest();
            entity.setId(request.getId());
            entity.setPetId(request.getPetId());
            entity.setRequesterId(request.getRequesterId());
            entity.setMessage(request.getMessage());
            entity.setStatus(request.getStatus() != null ? request.getStatus() : "submitted");
            entity.setSubmittedAt(request.getSubmittedAt());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createAdoptionRequest: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createAdoptionRequest: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException creating AdoptionRequest", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AdoptionRequest", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while creating AdoptionRequest", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Create multiple AdoptionRequests (batch)", description = "Create multiple AdoptionRequest entities in batch. Returns list of technicalIds.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class))))
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> batchCreateAdoptionRequests(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of AdoptionRequest creation payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateAdoptionRequestRequest.class))))
            @RequestBody List<CreateAdoptionRequestRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must be a non-empty array");
            }
            List<AdoptionRequest> entities = new ArrayList<>();
            for (CreateAdoptionRequestRequest r : requests) {
                if (r == null) continue;
                AdoptionRequest entity = new AdoptionRequest();
                entity.setId(r.getId());
                entity.setPetId(r.getPetId());
                entity.setRequesterId(r.getRequesterId());
                entity.setMessage(r.getMessage());
                entity.setStatus(r.getStatus() != null ? r.getStatus() : "submitted");
                entity.setSubmittedAt(r.getSubmittedAt());
                entities.add(entity);
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) stringIds.add(id.toString());
            }
            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for batchCreateAdoptionRequests: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during batchCreateAdoptionRequests: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during batchCreateAdoptionRequests: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during batchCreateAdoptionRequests", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while batch creating AdoptionRequests", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while batch creating AdoptionRequests", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve an AdoptionRequest entity by its technicalId.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionRequestResponse.class)))
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAdoptionRequestById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path variable is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(404).body("AdoptionRequest not found");
            }
            JsonNode dataNode = dataPayload.getData();
            AdoptionRequestResponse response = objectMapper.treeToValue(dataNode, AdoptionRequestResponse.class);
            response.setTechnicalId(technicalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getAdoptionRequestById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("AdoptionRequest not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getAdoptionRequestById: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException retrieving AdoptionRequest", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving AdoptionRequest", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving AdoptionRequest", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List AdoptionRequests", description = "Retrieve all AdoptionRequest entities (unpaged).")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionRequestResponse.class))))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listAdoptionRequests() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<AdoptionRequestResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null || payload.getData() == null || payload.getData().isNull()) continue;
                    AdoptionRequestResponse resp = objectMapper.treeToValue(payload.getData(), AdoptionRequestResponse.class);
                    // Try to extract technical id from meta if present
                    try {
                        JsonNode meta = payload.getMeta();
                        if (meta != null && !meta.isNull()) {
                            if (meta.has("technicalId")) resp.setTechnicalId(meta.get("technicalId").asText());
                            else if (meta.has("id")) resp.setTechnicalId(meta.get("id").asText());
                            else if (meta.has("technical_id")) resp.setTechnicalId(meta.get("technical_id").asText());
                        }
                    } catch (Exception ignore) {
                        // ignore meta parsing errors
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("AdoptionRequests not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during listAdoptionRequests: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException listing AdoptionRequests", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing AdoptionRequests", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while listing AdoptionRequests", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update AdoptionRequest", description = "Update an existing AdoptionRequest by technicalId.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateAdoptionRequestRequest.class)))
            @RequestBody UpdateAdoptionRequestRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path variable is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            AdoptionRequest entity = new AdoptionRequest();
            // Map fields from request (allow partial updates if service supports it)
            entity.setId(request.getId());
            entity.setPetId(request.getPetId());
            entity.setRequesterId(request.getRequesterId());
            entity.setMessage(request.getMessage());
            entity.setStatus(request.getStatus());
            entity.setSubmittedAt(request.getSubmittedAt());
            entity.setProcessedAt(request.getProcessedAt());
            entity.setProcessedBy(request.getProcessedBy());

            CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID uuid = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(uuid.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("AdoptionRequest not found during update: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during updateAdoptionRequest: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException updating AdoptionRequest", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating AdoptionRequest", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while updating AdoptionRequest", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete AdoptionRequest", description = "Delete an AdoptionRequest by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<?> deleteAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path variable is required");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID uuid = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(uuid.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteAdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("AdoptionRequest not found during delete: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during deleteAdoptionRequest: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException deleting AdoptionRequest", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting AdoptionRequest", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting AdoptionRequest", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @Schema(name = "CreateAdoptionRequestRequest", description = "Payload to create an AdoptionRequest")
    public static class CreateAdoptionRequestRequest {
        @Schema(description = "Business id for the request", example = "REQ-55", required = true)
        private String id;

        @Schema(description = "Reference to pet business id", example = "PET-123", required = true)
        private String petId;

        @Schema(description = "Reference to requester business id", example = "OWN-10", required = true)
        private String requesterId;

        @Schema(description = "Optional message from requester", example = "I have a loving home")
        private String message;

        @Schema(description = "Status of the request", example = "submitted")
        private String status;

        @Schema(description = "ISO timestamp when submitted", example = "2025-08-28T12:05:00Z", required = true)
        private String submittedAt;
    }

    @Data
    @Schema(name = "UpdateAdoptionRequestRequest", description = "Payload to update an AdoptionRequest")
    public static class UpdateAdoptionRequestRequest {
        @Schema(description = "Business id for the request", example = "REQ-55")
        private String id;

        @Schema(description = "Reference to pet business id", example = "PET-123")
        private String petId;

        @Schema(description = "Reference to requester business id", example = "OWN-10")
        private String requesterId;

        @Schema(description = "Optional message from requester", example = "I have a loving home")
        private String message;

        @Schema(description = "Status of the request", example = "under_review")
        private String status;

        @Schema(description = "ISO timestamp when submitted", example = "2025-08-28T12:05:00Z")
        private String submittedAt;

        @Schema(description = "ISO timestamp when processed", example = "2025-08-28T13:00:00Z")
        private String processedAt;

        @Schema(description = "Staff id who processed the request", example = "STAFF-1")
        private String processedBy;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Technical id response returned after entity creation/update/delete")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "tech-req-0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Batch technical ids response returned after batch creation")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids returned", example = "[\"tech-req-0001\",\"tech-req-0002\"]")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "AdoptionRequestResponse", description = "AdoptionRequest retrieval response")
    public static class AdoptionRequestResponse {
        @Schema(description = "Technical id of the persisted entity", example = "tech-req-0001")
        private String technicalId;

        @Schema(description = "Business id for the request", example = "REQ-55")
        private String id;

        @Schema(description = "Reference to pet business id", example = "PET-123")
        private String petId;

        @Schema(description = "Reference to requester business id", example = "OWN-10")
        private String requesterId;

        @Schema(description = "Optional message from requester", example = "I have a loving home")
        private String message;

        @Schema(description = "Status of the request", example = "under_review")
        private String status;

        @Schema(description = "ISO timestamp when submitted", example = "2025-08-28T12:05:00Z")
        private String submittedAt;

        @Schema(description = "ISO timestamp when processed", example = "2025-08-28T13:00:00Z")
        private String processedAt;

        @Schema(description = "Staff id who processed the request", example = "STAFF-1")
        private String processedBy;
    }
}