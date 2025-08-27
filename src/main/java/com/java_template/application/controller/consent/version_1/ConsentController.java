package com.java_template.application.controller.consent.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.consent.version_1.Consent;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/consents")
@Tag(name = "Consent", description = "Consent entity proxy controller (version 1)")
public class ConsentController {
    private static final Logger logger = LoggerFactory.getLogger(ConsentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Consent", description = "Persist a new Consent entity and start associated workflows")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createConsent(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Consent create request", required = true,
            content = @Content(schema = @Schema(implementation = ConsentCreateRequest.class))) @RequestBody ConsentCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getUserId() == null || request.getUserId().isBlank())
                throw new IllegalArgumentException("userId is required");
            if (request.getType() == null || request.getType().isBlank())
                throw new IllegalArgumentException("type is required");

            Consent consent = new Consent();
            // basic mapping only; business rules and workflow processing happen in services/workflows
            consent.setConsentId(UUID.randomUUID().toString());
            consent.setUserId(request.getUserId());
            consent.setType(request.getType());
            consent.setSource(request.getSource());
            // ensure required fields exist for persistence
            consent.setRequestedAt(Instant.now().toString());
            consent.setStatus("requested");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    consent
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create consent: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating consent", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating consent", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating consent", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Consents", description = "Persist multiple Consent entities in bulk")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<TechnicalIdsResponse> createConsentsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk consent create request", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConsentCreateRequest.class)))) @RequestBody List<ConsentCreateRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body is required and must contain at least one item");

            List<Consent> consents = new ArrayList<>();
            for (ConsentCreateRequest request : requests) {
                if (request == null) continue;
                if (request.getUserId() == null || request.getUserId().isBlank())
                    throw new IllegalArgumentException("userId is required for all items");
                if (request.getType() == null || request.getType().isBlank())
                    throw new IllegalArgumentException("type is required for all items");

                Consent consent = new Consent();
                consent.setConsentId(UUID.randomUUID().toString());
                consent.setUserId(request.getUserId());
                consent.setType(request.getType());
                consent.setSource(request.getSource());
                consent.setRequestedAt(Instant.now().toString());
                consent.setStatus("requested");
                consents.add(consent);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    consents
            );

            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) stringIds.add(id.toString());
            }
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk request to create consents: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating consents bulk", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating consents bulk", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating consents bulk", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Consent by technicalId", description = "Retrieve a Consent entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ConsentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ConsentResponse> getConsentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(404).build();

            ConsentResponse resp = objectMapper.treeToValue(node, ConsentResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get consent: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving consent", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving consent", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving consent", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Consents", description = "Retrieve all Consent entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConsentResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<ConsentResponse>> listConsents() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            List<ConsentResponse> list = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> {
                    try {
                        ConsentResponse cr = objectMapper.treeToValue(node, ConsentResponse.class);
                        list.add(cr);
                    } catch (Exception ex) {
                        logger.warn("Failed to map consent node to response DTO", ex);
                    }
                });
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException e) {
            logger.error("ExecutionException while listing consents", e);
            return ResponseEntity.status(500).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing consents", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while listing consents", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Consents", description = "Retrieve Consent entities matching provided search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConsentResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<List<ConsentResponse>> searchConsents(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class))) @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();
            List<ConsentResponse> list = new ArrayList<>();
            if (array != null) {
                array.forEach(node -> {
                    try {
                        ConsentResponse cr = objectMapper.treeToValue(node, ConsentResponse.class);
                        list.add(cr);
                    } catch (Exception ex) {
                        logger.warn("Failed to map consent node to response DTO", ex);
                    }
                });
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request for consents: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while searching consents", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching consents", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while searching consents", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Consent", description = "Update an existing Consent entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateConsent(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Consent update request", required = true,
                    content = @Content(schema = @Schema(implementation = ConsentCreateRequest.class))) @RequestBody ConsentCreateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Consent consent = new Consent();
            consent.setConsentId(technicalId);
            if (request.getUserId() != null) consent.setUserId(request.getUserId());
            if (request.getType() != null) consent.setType(request.getType());
            if (request.getSource() != null) consent.setSource(request.getSource());
            // do not modify workflow-managed timestamps/status here

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    consent
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to update consent: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while updating consent", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating consent", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while updating consent", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Consent", description = "Delete a Consent entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteConsent(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to delete consent: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while deleting consent", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting consent", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while deleting consent", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Static DTO classes

    @Data
    public static class ConsentCreateRequest {
        @Schema(description = "Reference to the user (UUID as string)", example = "user-123")
        private String userId;

        @Schema(description = "Consent type (e.g., marketing, analytics)", example = "marketing")
        private String type;

        @Schema(description = "Source of the consent (e.g., signup_form)", example = "signup_form")
        private String source;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids of persisted entities")
        private List<String> technicalIds;
    }

    @Data
    public static class ConsentResponse {
        @Schema(description = "Technical id of the consent", example = "consent-123")
        private String consentId;

        @Schema(description = "Reference to user", example = "user-123")
        private String userId;

        @Schema(description = "Consent type", example = "marketing")
        private String type;

        @Schema(description = "Consent status", example = "active")
        private String status;

        @Schema(description = "Reference to evidence", example = "evidence-uuid")
        private String evidenceRef;

        @Schema(description = "Requested timestamp (ISO-8601)", example = "2025-08-27T12:34:56Z")
        private String requestedAt;

        @Schema(description = "Granted timestamp (ISO-8601)", example = "2025-08-27T12:35:56Z")
        private String grantedAt;

        @Schema(description = "Revoked timestamp (ISO-8601)", example = "2025-09-01T10:00:00Z")
        private String revokedAt;

        @Schema(description = "Source of the consent", example = "signup_form")
        private String source;
    }
}