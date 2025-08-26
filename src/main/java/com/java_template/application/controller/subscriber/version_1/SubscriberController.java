package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/subscribers/v1")
@Tag(name = "SubscriberController", description = "APIs for Subscriber entity (version 1). Controller is a proxy to EntityService.")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register Subscriber", description = "Create a new Subscriber. Controller proxies to EntityService.addItem and returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = RegisterSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> registerSubscriber(@RequestBody RegisterSubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Subscriber data = new Subscriber();
            data.setName(request.getName());
            data.setEmail(request.getEmail());
            data.setPreferences(request.getPreferences());
            // controller must remain a proxy; do not apply business rules here

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            RegisterSubscriberResponse resp = new RegisterSubscriberResponse();
            resp.setTechnicalId(technicalId == null ? null : technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for registerSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in registerSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while registering subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in registerSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Register Subscribers (batch)", description = "Create multiple Subscribers. Controller proxies to EntityService.addItems and returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = RegisterSubscribersBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> registerSubscribersBatch(@RequestBody List<RegisterSubscriberRequest> requests) {
        try {
            if (requests == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            List<Subscriber> entities = new ArrayList<>();
            for (RegisterSubscriberRequest r : requests) {
                Subscriber s = new Subscriber();
                s.setName(r.getName());
                s.setEmail(r.getEmail());
                s.setPreferences(r.getPreferences());
                entities.add(s);
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) {
                    stringIds.add(u == null ? null : u.toString());
                }
            }
            RegisterSubscribersBatchResponse resp = new RegisterSubscribersBatchResponse();
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for registerSubscribersBatch: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in registerSubscribersBatch", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while registering subscribers batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in registerSubscribersBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by technicalId. Controller proxies to EntityService.getItem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            SubscriberResponse resp = objectMapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getSubscriberById", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscribers. Controller proxies to EntityService.getItems.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<SubscriberResponse> list = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    SubscriberResponse r = objectMapper.treeToValue(array.get(i), SubscriberResponse.class);
                    list.add(r);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllSubscribers", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllSubscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter Subscribers", description = "Filter Subscribers using SearchConditionRequest. Controller proxies to EntityService.getItemsByCondition.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterSubscribers(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredFuture.get();
            List<SubscriberResponse> list = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    SubscriberResponse r = objectMapper.treeToValue(array.get(i), SubscriberResponse.class);
                    list.add(r);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for filterSubscribers: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in filterSubscribers", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in filterSubscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update a Subscriber by technicalId. Controller proxies to EntityService.updateItem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody UpdateSubscriberRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Subscriber data = new Subscriber();
            data.setName(request.getName());
            data.setEmail(request.getEmail());
            data.setPreferences(request.getPreferences());
            data.setActive(request.getActive());
            data.setLastDeliveryStatus(request.getLastDeliveryStatus());
            data.setOptOutAt(request.getOptOutAt());
            data.setCreatedAt(request.getCreatedAt());
            data.setId(request.getId());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );
            UUID updated = updatedFuture.get();
            UpdateSubscriberResponse resp = new UpdateSubscriberResponse();
            resp.setTechnicalId(updated == null ? null : updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in updateSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId. Controller proxies to EntityService.deleteItem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deleted = deletedFuture.get();
            DeleteSubscriberResponse resp = new DeleteSubscriberResponse();
            resp.setTechnicalId(deleted == null ? null : deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in deleteSubscriber", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "RegisterSubscriberRequest", description = "Request payload to register a subscriber")
    public static class RegisterSubscriberRequest {
        @Schema(description = "Subscriber name", example = "Alice")
        private String name;
        @Schema(description = "Subscriber email", example = "alice@example.com")
        private String email;
        @Schema(description = "Preferences map", example = "{\"reportType\":\"summary\",\"frequency\":\"daily\"}")
        private Map<String, String> preferences;
    }

    @Data
    @Schema(name = "RegisterSubscriberResponse", description = "Response payload after registering subscriber")
    public static class RegisterSubscriberResponse {
        @Schema(description = "Technical ID of the created subscriber", example = "sub_222")
        private String technicalId;
    }

    @Data
    @Schema(name = "RegisterSubscribersBatchResponse", description = "Response payload after registering multiple subscribers")
    public static class RegisterSubscribersBatchResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber entity representation returned by GET")
    public static class SubscriberResponse {
        @Schema(description = "Technical id", example = "sub_222")
        private String id;
        @Schema(description = "Name", example = "Alice")
        private String name;
        @Schema(description = "Email", example = "alice@example.com")
        private String email;
        @Schema(description = "Preferences map", example = "{\"reportType\":\"summary\",\"frequency\":\"daily\"}")
        private Map<String, String> preferences;
        @Schema(description = "Active flag", example = "true")
        private Boolean active;
        @Schema(description = "Last delivery status", example = "SUCCESS")
        private String lastDeliveryStatus;
        @Schema(description = "Opt-out timestamp", example = "2025-08-01T12:00:00Z")
        private String optOutAt;
        @Schema(description = "Created at timestamp", example = "2025-08-01T11:50:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateSubscriberRequest", description = "Request payload to update a subscriber")
    public static class UpdateSubscriberRequest {
        @Schema(description = "Technical id (optional, can be ignored by service)", example = "sub_222")
        private String id;
        @Schema(description = "Name", example = "Alice")
        private String name;
        @Schema(description = "Email", example = "alice@example.com")
        private String email;
        @Schema(description = "Preferences map", example = "{\"reportType\":\"summary\",\"frequency\":\"daily\"}")
        private Map<String, String> preferences;
        @Schema(description = "Active flag", example = "true")
        private Boolean active;
        @Schema(description = "Last delivery status", example = "SUCCESS")
        private String lastDeliveryStatus;
        @Schema(description = "Opt-out timestamp", example = "2025-08-01T12:00:00Z")
        private String optOutAt;
        @Schema(description = "Created at timestamp (optional)", example = "2025-08-01T11:50:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "UpdateSubscriberResponse", description = "Response payload after updating subscriber")
    public static class UpdateSubscriberResponse {
        @Schema(description = "Technical ID of the updated subscriber", example = "sub_222")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteSubscriberResponse", description = "Response payload after deleting subscriber")
    public static class DeleteSubscriberResponse {
        @Schema(description = "Technical ID of the deleted subscriber", example = "sub_222")
        private String technicalId;
    }
}