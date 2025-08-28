package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;

@RestController
@RequestMapping("/api/v1/subscriber")
@Tag(name = "Subscriber", description = "Subscriber entity proxy controller (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Add Subscriber", description = "Add a single Subscriber entity. Returns the technicalId (UUID) of the created entity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addSubscriber(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Subscriber to create", content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
                                           @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Subscriber entity = new Subscriber();
            // use provided subscriber_id or generate one
            String sid = request.getSubscriberId() != null && !request.getSubscriberId().isBlank()
                    ? request.getSubscriberId()
                    : UUID.randomUUID().toString();
            entity.setSubscriberId(sid);
            entity.setEmail(request.getEmail());
            entity.setName(request.getName());
            entity.setFilters(request.getFilters());
            entity.setFrequency(request.getFrequency());
            entity.setStatus(request.getStatus());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(entityId.toString());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid addSubscriber request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while adding subscriber", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while adding subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add Subscribers (batch)", description = "Add multiple Subscriber entities. Returns list of technicalIds (UUIDs).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addSubscribers(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "List of subscribers to create", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateSubscriberRequest.class))))
                                            @RequestBody List<CreateSubscriberRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<Subscriber> entities = new ArrayList<>();
            for (CreateSubscriberRequest r : requests) {
                Subscriber entity = new Subscriber();
                String sid = r.getSubscriberId() != null && !r.getSubscriberId().isBlank()
                        ? r.getSubscriberId()
                        : UUID.randomUUID().toString();
                entity.setSubscriberId(sid);
                entity.setEmail(r.getEmail());
                entity.setName(r.getName());
                entity.setFilters(r.getFilters());
                entity.setFrequency(r.getFrequency());
                entity.setStatus(r.getStatus());
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> result = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) result.add(u.toString());
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid addSubscribers request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while adding subscribers", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while adding subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            JsonNode node = dataPayload != null ? (JsonNode) dataPayload.getData() : null;
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            SubscriberResponse response = objectMapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid getSubscriber request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while getting subscriber", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscriber entities (page & filtering not implemented).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listSubscribers() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<SubscriberResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null && !data.isNull()) {
                        SubscriberResponse resp = objectMapper.treeToValue(data, SubscriberResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while listing subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while listing subscribers", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while listing subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update a Subscriber entity by technicalId. Returns the technicalId of the updated entity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Subscriber update payload", content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Subscriber entity = new Subscriber();
            // Keep the technicalId consistent
            entity.setSubscriberId(request.getSubscriberId() != null && !request.getSubscriberId().isBlank()
                    ? request.getSubscriberId()
                    : UUID.fromString(technicalId).toString());
            entity.setEmail(request.getEmail());
            entity.setName(request.getName());
            entity.setFilters(request.getFilters());
            entity.setFrequency(request.getFrequency());
            entity.setStatus(request.getStatus());

            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updated.get();
            return ResponseEntity.ok(updatedId.toString());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid updateSubscriber request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while updating subscriber", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber entity by technicalId. Returns the technicalId of the deleted entity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deleted = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deleted.get();
            return ResponseEntity.ok(deletedId.toString());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid deleteSubscriber request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while deleting subscriber", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes for request/response

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Payload to create or update a Subscriber")
    public static class CreateSubscriberRequest {
        @JsonProperty("subscriber_id")
        @Schema(description = "Internal subscriber id (optional). If not provided one will be generated", example = "sub-1")
        private String subscriberId;

        @JsonProperty("email")
        @Schema(description = "Recipient email", example = "user@example.com", required = true)
        private String email;

        @JsonProperty("name")
        @Schema(description = "Display name", example = "Alice")
        private String name;

        @JsonProperty("frequency")
        @Schema(description = "Delivery frequency", example = "weekly", required = true)
        private String frequency;

        @JsonProperty("status")
        @Schema(description = "Subscriber status", example = "ACTIVE", required = true)
        private String status;

        @JsonProperty("filters")
        @Schema(description = "Subscriber filters (serialized)", example = "area=NW")
        private String filters;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber entity representation returned by APIs")
    public static class SubscriberResponse {
        @JsonProperty("subscriber_id")
        @Schema(description = "Internal subscriber id", example = "sub-1")
        private String subscriberId;

        @JsonProperty("email")
        @Schema(description = "Recipient email", example = "user@example.com")
        private String email;

        @JsonProperty("name")
        @Schema(description = "Display name", example = "Alice")
        private String name;

        @JsonProperty("frequency")
        @Schema(description = "Delivery frequency", example = "weekly")
        private String frequency;

        @JsonProperty("status")
        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        @JsonProperty("filters")
        @Schema(description = "Subscriber filters (serialized)", example = "area=NW")
        private String filters;
    }
}