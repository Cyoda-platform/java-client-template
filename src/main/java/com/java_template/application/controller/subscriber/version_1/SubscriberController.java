package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber", description = "Subscriber entity endpoints (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Register Subscriber", description = "Register a new subscriber and trigger verification workflow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber registration request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (isBlank(request.getName())) {
                throw new IllegalArgumentException("name is required");
            }
            if (isBlank(request.getContactType())) {
                throw new IllegalArgumentException("contactType is required");
            }
            if (isBlank(request.getContactDetails())) {
                throw new IllegalArgumentException("contactDetails is required");
            }

            // Map request to entity (no business logic here)
            Subscriber entity = new Subscriber();
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactDetails(request.getContactDetails());
            if (request.getPreferences() != null) {
                Subscriber.Preferences prefs = new Subscriber.Preferences();
                prefs.setFrequency(request.getPreferences().getFrequency());
                prefs.setSpecies(request.getPreferences().getSpecies());
                prefs.setTags(request.getPreferences().getTags());
                entity.setPreferences(prefs);
            }
            // createdAt, id, verified, active will be handled by workflows/persistence

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while creating subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Bulk Register Subscribers", description = "Register multiple subscribers in a single request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<TechnicalIdsResponse> createSubscribersBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk subscriber registration request", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateSubscriberRequest.class))))
            @RequestBody CreateSubscribersRequest request) {
        try {
            if (request == null || request.getSubscribers() == null || request.getSubscribers().isEmpty()) {
                throw new IllegalArgumentException("subscribers list is required");
            }

            List<Subscriber> entities = request.getSubscribers().stream().map(req -> {
                Subscriber s = new Subscriber();
                s.setName(req.getName());
                s.setContactType(req.getContactType());
                s.setContactDetails(req.getContactDetails());
                if (req.getPreferences() != null) {
                    Subscriber.Preferences prefs = new Subscriber.Preferences();
                    prefs.setFrequency(req.getPreferences().getFrequency());
                    prefs.setSpecies(req.getPreferences().getSpecies());
                    prefs.setTags(req.getPreferences().getTags());
                    s.setPreferences(prefs);
                }
                return s;
            }).toList();

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();

            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while bulk creating subscribers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while bulk creating subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (isBlank(technicalId)) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();

            // Optionally ensure the response matches expected DTO shape; we return the raw ObjectNode from the service
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while retrieving subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all subscribers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                logger.error("ExecutionException while retrieving all subscribers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Subscribers", description = "Retrieve subscribers by condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<ArrayNode> searchSubscribers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while searching subscribers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while searching subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (isBlank(technicalId)) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactDetails(request.getContactDetails());
            if (request.getPreferences() != null) {
                Subscriber.Preferences prefs = new Subscriber.Preferences();
                prefs.setFrequency(request.getPreferences().getFrequency());
                prefs.setSpecies(request.getPreferences().getSpecies());
                prefs.setTags(request.getPreferences().getTags());
                entity.setPreferences(prefs);
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while updating subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (isBlank(technicalId)) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while deleting subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create a subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Subscriber display name", example = "Alex")
        private String name;

        @Schema(description = "Contact type (email, webhook, sms)", example = "email")
        private String contactType;

        @Schema(description = "Contact details (email address, webhook URL, or phone)", example = "alex@example.com")
        private String contactDetails;

        @Schema(description = "Subscriber preferences")
        private PreferencesDto preferences;
    }

    @Data
    @Schema(name = "CreateSubscribersRequest", description = "Bulk create subscribers request")
    public static class CreateSubscribersRequest {
        @Schema(description = "List of subscribers to create")
        private List<CreateSubscriberRequest> subscribers;
    }

    @Data
    @Schema(name = "PreferencesDto", description = "Subscriber preferences")
    public static class PreferencesDto {
        @Schema(description = "Preferred species", example = "[\"cat\"]")
        private java.util.List<String> species;

        @Schema(description = "Preferred tags", example = "[\"kitten\"]")
        private java.util.List<String> tags;

        @Schema(description = "Notification frequency (immediate, digest)", example = "immediate")
        private String frequency;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id of created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "sub_xyz789")
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing the technical ids of created entities")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Internal id", example = "sub_xyz789")
        private String id;

        @Schema(description = "Subscriber display name", example = "Alex")
        private String name;

        @Schema(description = "Contact type", example = "email")
        private String contactType;

        @Schema(description = "Contact details", example = "alex@example.com")
        private String contactDetails;

        @Schema(description = "Subscriber preferences")
        private PreferencesDto preferences;

        @Schema(description = "Verified state")
        private boolean verified;

        @Schema(description = "Active (subscribed) state")
        private boolean active;

        @Schema(description = "Created at timestamp", example = "2025-08-25T12:05:00Z")
        private String createdAt;

        @Schema(description = "Updated at timestamp", example = "2025-08-25T12:05:00Z")
        private String updatedAt;
    }
}