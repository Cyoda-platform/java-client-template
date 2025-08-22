package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for AdoptionRequest entity.
 * All business logic lives in workflows/processors; controller only proxies to EntityService.
 */
@RestController
@RequestMapping("/api/v1/adoption-requests")
@Tag(name = "AdoptionRequest", description = "AdoptionRequest entity proxy API (version 1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Create an AdoptionRequest. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAdoptionRequest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAdoptionRequest.class)))
            @RequestBody CreateAdoptionRequest req) {
        try {
            if (req == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            AdoptionRequest data = new AdoptionRequest();
            // map fields (controller must not implement business logic, only map fields)
            data.setId(req.getId());
            data.setPetId(req.getPetId());
            data.setOwnerId(req.getOwnerId());
            data.setPreferredPickupDate(req.getPreferredPickupDate());
            data.setNotes(req.getNotes());
            data.setRequestDate(req.getRequestDate());
            data.setStatus(req.getStatus());
            data.setDecisionBy(req.getDecisionBy());
            data.setDecisionDate(req.getDecisionDate());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in createAdoptionRequest: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createAdoptionRequest", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in createAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in createAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve an AdoptionRequest by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAdoptionRequestById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
            }
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in getAdoptionRequestById: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAdoptionRequestById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getAdoptionRequestById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in getAdoptionRequestById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List all AdoptionRequests", description = "Retrieve all AdoptionRequests.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionRequestResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listAdoptionRequests() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listAdoptionRequests", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in listAdoptionRequests", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in listAdoptionRequests", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Filter AdoptionRequests", description = "Filter AdoptionRequests using a SearchConditionRequest.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionRequestResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchAdoptionRequests(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in searchAdoptionRequests: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchAdoptionRequests", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in searchAdoptionRequests", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in searchAdoptionRequests", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update AdoptionRequest", description = "Update an AdoptionRequest by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateAdoptionRequest.class)))
            @RequestBody UpdateAdoptionRequest req) {
        try {
            if (req == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = UUID.fromString(technicalId);

            AdoptionRequest data = new AdoptionRequest();
            data.setId(req.getId());
            data.setPetId(req.getPetId());
            data.setOwnerId(req.getOwnerId());
            data.setPreferredPickupDate(req.getPreferredPickupDate());
            data.setNotes(req.getNotes());
            data.setRequestDate(req.getRequestDate());
            data.setStatus(req.getStatus());
            data.setDecisionBy(req.getDecisionBy());
            data.setDecisionDate(req.getDecisionDate());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id,
                    data
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in updateAdoptionRequest: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateAdoptionRequest", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in updateAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in updateAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete AdoptionRequest", description = "Delete an AdoptionRequest by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteAdoptionRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("IllegalArgumentException in deleteAdoptionRequest: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteAdoptionRequest", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in deleteAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in deleteAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // -------------------- DTOs --------------------

    @Data
    @Schema(name = "CreateAdoptionRequest", description = "Payload to create an AdoptionRequest")
    public static class CreateAdoptionRequest {
        @Schema(description = "Domain id (optional)", example = "req_222")
        private String id;

        @Schema(description = "Target pet id", required = true, example = "pet_555")
        private String petId;

        @Schema(description = "Requesting owner id", required = true, example = "owner_98765")
        private String ownerId;

        @Schema(description = "ISO timestamp of request", example = "2025-08-20T12:00:00Z")
        private String requestDate;

        @Schema(description = "Status of the request", example = "requested")
        private String status;

        @Schema(description = "Applicant notes", example = "I have a fenced yard and other pets")
        private String notes;

        @Schema(description = "Preferred pickup date (ISO date)", example = "2025-09-01")
        private String preferredPickupDate;

        @Schema(description = "Admin who made decision (if any)", example = "admin_1")
        private String decisionBy;

        @Schema(description = "Decision date (ISO timestamp)", example = "2025-08-21T08:00:00Z")
        private String decisionDate;
    }

    @Data
    @Schema(name = "UpdateAdoptionRequest", description = "Payload to update an AdoptionRequest")
    public static class UpdateAdoptionRequest {
        @Schema(description = "Domain id (optional)", example = "req_222")
        private String id;

        @Schema(description = "Target pet id", example = "pet_555")
        private String petId;

        @Schema(description = "Requesting owner id", example = "owner_98765")
        private String ownerId;

        @Schema(description = "ISO timestamp of request", example = "2025-08-20T12:00:00Z")
        private String requestDate;

        @Schema(description = "Status of the request", example = "under_review")
        private String status;

        @Schema(description = "Applicant notes", example = "I have a fenced yard and other pets")
        private String notes;

        @Schema(description = "Preferred pickup date (ISO date)", example = "2025-09-01")
        private String preferredPickupDate;

        @Schema(description = "Admin who made decision (if any)", example = "admin_1")
        private String decisionBy;

        @Schema(description = "Decision date (ISO timestamp)", example = "2025-08-21T08:00:00Z")
        private String decisionDate;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created/updated/deleted entity", example = "11111111-1111-1111-1111-111111111111")
        private String technicalId;
    }

    @Data
    @Schema(name = "AdoptionRequestResponse", description = "AdoptionRequest response payload")
    public static class AdoptionRequestResponse {
        @Schema(description = "Technical ID", example = "11111111-1111-1111-1111-111111111111")
        private String technicalId;

        @Schema(description = "Domain id", example = "req_222")
        private String id;

        @Schema(description = "Target pet id", example = "pet_555")
        private String petId;

        @Schema(description = "Requesting owner id", example = "owner_98765")
        private String ownerId;

        @Schema(description = "Status", example = "under_review")
        private String status;

        @Schema(description = "ISO timestamp of request", example = "2025-08-20T12:00:00Z")
        private String requestDate;

        @Schema(description = "Preferred pickup date (ISO date)", example = "2025-09-01")
        private String preferredPickupDate;

        @Schema(description = "Applicant notes", example = "I have a fenced yard and other pets")
        private String notes;

        @Schema(description = "Admin who made decision (if any)", example = "admin_1")
        private String decisionBy;

        @Schema(description = "Decision date (ISO timestamp)", example = "2025-08-21T08:00:00Z")
        private String decisionDate;
    }
}