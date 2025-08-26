package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/subscribers")
@Tag(name = "Subscriber API", description = "Subscriber entity endpoints (version 1)")
public class SubscriberController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Create a new Subscriber. Response contains only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
            content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
                                            @Valid @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setType(request.getType());
            entity.setContact(request.getContact());
            entity.setFilters(request.getFilters());
            entity.setActive(request.getActive());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on createSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in createSubscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Subscribers", description = "Create multiple Subscribers in bulk. Response contains list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscribersBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk create request", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateSubscriberRequest.class))))
                                                     @Valid @RequestBody List<CreateSubscriberRequest> requests) {
        try {
            if (requests == null) throw new IllegalArgumentException("Request body is required");

            List<Subscriber> entities = new ArrayList<>();
            for (CreateSubscriberRequest request : requests) {
                Subscriber entity = new Subscriber();
                entity.setId(request.getId());
                entity.setType(request.getType());
                entity.setContact(request.getContact());
                entity.setFilters(request.getFilters());
                entity.setActive(request.getActive());
                entity.setCreatedAt(request.getCreatedAt());
                entity.setLastNotifiedAt(request.getLastNotifiedAt());
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );

            List<UUID> uuids = idsFuture.get();
            IdsResponse resp = new IdsResponse();
            List<String> ids = new ArrayList<>();
            for (UUID u : uuids) ids.add(u.toString());
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscribersBulk: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on createSubscribersBulk", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscribers bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in createSubscribersBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberFullResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriberByTechnicalId(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                                        @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            SubscriberFullResponse resp = mapObjectNodeToResponse(technicalId, node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberByTechnicalId: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on getSubscriberByTechnicalId", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in getSubscriberByTechnicalId", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscribers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberFullResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<SubscriberFullResponse> result = new ArrayList<>();
            for (JsonNode node : array) {
                String techId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                result.add(mapObjectNodeToResponse(techId, (ObjectNode) node));
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on getAllSubscribers", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in getAllSubscribers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Subscribers", description = "Retrieve Subscribers by a simple search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberFullResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchSubscribers(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                             @Valid @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<SubscriberFullResponse> result = new ArrayList<>();
            for (JsonNode node : array) {
                String techId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                result.add(mapObjectNodeToResponse(techId, (ObjectNode) node));
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchSubscribers: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on searchSubscribers", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in searchSubscribers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing Subscriber by technicalId. Response contains technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSubscriber(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                              @PathVariable("technicalId") String technicalId,
                                              @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update request", required = true,
                                                      content = @Content(schema = @Schema(implementation = UpdateSubscriberRequest.class)))
                                              @Valid @RequestBody UpdateSubscriberRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setType(request.getType());
            entity.setContact(request.getContact());
            entity.setFilters(request.getFilters());
            entity.setActive(request.getActive());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );
            UUID respId = updatedId.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(respId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateSubscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on updateSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in updateSubscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId. Response contains technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteSubscriber(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                              @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<java.util.UUID> deletedId = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID respId = deletedId.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(respId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteSubscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException on deleteSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in deleteSubscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Utility to map ObjectNode to DTO
    private SubscriberFullResponse mapObjectNodeToResponse(String technicalId, ObjectNode node) {
        SubscriberFullResponse resp = new SubscriberFullResponse();
        resp.setTechnicalId(technicalId);
        if (node == null) return resp;
        resp.setId(node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null);
        resp.setType(node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null);
        resp.setContact(node.has("contact") && !node.get("contact").isNull() ? node.get("contact").asText() : null);
        if (node.has("active") && !node.get("active").isNull()) resp.setActive(node.get("active").asBoolean());
        resp.setFilters(node.has("filters") && !node.get("filters").isNull() ? node.get("filters").asText() : null);
        resp.setLastNotifiedAt(node.has("lastNotifiedAt") && !node.get("lastNotifiedAt").isNull() ? node.get("lastNotifiedAt").asText() : null);
        resp.setCreatedAt(node.has("createdAt") && !node.get("createdAt").isNull() ? node.get("createdAt").asText() : null);
        return resp;
    }

    // DTOs

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-01")
        private String id;

        @Schema(description = "Subscriber type (email|webhook|other)", example = "email")
        @NotBlank
        private String type;

        @Schema(description = "Contact (email address or webhook URL)", example = "ops@example.com")
        @NotBlank
        private String contact;

        @Schema(description = "Boolean active flag")
        @NotNull
        private Boolean active = Boolean.TRUE;

        @Schema(description = "JSON string describing filters", example = "{\"category\":\"Chemistry\"}")
        private String filters;

        @Schema(description = "Timestamp of last notification (optional)")
        private String lastNotifiedAt;

        @Schema(description = "Created at timestamp (optional, often set by server)")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateSubscriberRequest", description = "Request payload to update a Subscriber")
    public static class UpdateSubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-01")
        private String id;

        @Schema(description = "Subscriber type (email|webhook|other)", example = "email")
        @NotBlank
        private String type;

        @Schema(description = "Contact (email address or webhook URL)", example = "ops@example.com")
        @NotBlank
        private String contact;

        @Schema(description = "Boolean active flag")
        @NotNull
        private Boolean active = Boolean.TRUE;

        @Schema(description = "JSON string describing filters", example = "{\"category\":\"Chemistry\"}")
        private String filters;

        @Schema(description = "Timestamp of last notification (optional)")
        private String lastNotifiedAt;

        @Schema(description = "Created at timestamp (optional, often set by server)")
        private String createdAt;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing technicalId")
    public static class IdResponse {
        @Schema(description = "Technical ID of the entity", example = "tx-sub-0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing multiple technicalIds")
    public static class IdsResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "SubscriberFullResponse", description = "Full Subscriber response including technicalId and entity fields")
    public static class SubscriberFullResponse {
        @Schema(description = "Technical ID of the entity", example = "tx-sub-0001")
        private String technicalId;

        @Schema(description = "Business id of the subscriber", example = "sub-01")
        private String id;

        @Schema(description = "Subscriber type (email|webhook|other)", example = "email")
        private String type;

        @Schema(description = "Contact (email address or webhook URL)", example = "ops@example.com")
        private String contact;

        @Schema(description = "Boolean active flag")
        private Boolean active;

        @Schema(description = "JSON string describing filters", example = "{\"category\":\"Chemistry\"}")
        private String filters;

        @Schema(description = "Timestamp of last notification", example = "2025-08-26T10:00:05Z")
        private String lastNotifiedAt;

        @Schema(description = "Created at timestamp", example = "2025-08-26T10:00:00Z")
        private String createdAt;
    }
}