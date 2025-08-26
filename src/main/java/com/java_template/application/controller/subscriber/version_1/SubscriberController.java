package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
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
@RequestMapping(path = "/api/v1/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriber", description = "Subscriber entity proxy API (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register Subscriber", description = "Register a new Subscriber. Persists the entity and returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber payload", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest requestBody
    ) {
        try {
            if (requestBody == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = mapToEntity(requestBody);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Bulk Register Subscribers", description = "Register multiple Subscribers. Persists the entities and returns the technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscribersBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Subscriber payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberRequest.class))))
            @RequestBody List<SubscriberRequest> requestBody
    ) {
        try {
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one subscriber");
            }

            List<Subscriber> entities = new ArrayList<>();
            for (SubscriberRequest r : requestBody) {
                entities.add(mapToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );

            List<UUID> uuids = idsFuture.get();
            BulkCreateResponse resp = new BulkCreateResponse();
            List<String> ids = new ArrayList<>();
            for (UUID u : uuids) ids.add(u.toString());
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when bulk creating subscribers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when bulk creating subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve a Subscriber by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr
    ) {
        try {
            UUID technicalId = UUID.fromString(technicalIdStr);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(404).body("Subscriber not found");
            }

            // Ensure technicalId is present in response payload
            node.put("technicalId", technicalId.toString());

            SubscriberResponse resp = MAPPER.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId format: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "List Subscribers", description = "Retrieve all Subscribers.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = "!technicalId")
    public ResponseEntity<?> listSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            // Map to typed response list
            List<SubscriberResponse> list = MAPPER.convertValue(arr, new TypeReference<List<SubscriberResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing subscribers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when listing subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Subscribers", description = "Search for Subscribers by a single field condition. Supports basic operators.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchSubscribers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequest.class)))
            @RequestBody SearchRequest request
    ) {
        try {
            if (request == null || request.getField() == null || request.getOperator() == null || request.getValue() == null) {
                throw new IllegalArgumentException("field, operator and value are required");
            }

            Condition cond = Condition.of("$. " + request.getField(), request.getOperator(), request.getValue());
            // The Condition.of expects JSONPath like "$.fieldName" but above has a space; fix to produce correct path:
            cond = Condition.of("$." + request.getField(), request.getOperator(), request.getValue());

            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    conditionRequest,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            List<SubscriberResponse> list = MAPPER.convertValue(arr, new TypeReference<List<SubscriberResponse>>() {});
            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching subscribers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when searching subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing Subscriber by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber payload", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest requestBody
    ) {
        try {
            UUID technicalId = UUID.fromString(technicalIdStr);
            if (requestBody == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = mapToEntity(requestBody);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId,
                    entity
            );

            UUID updatedId = updatedIdFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when updating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<?> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr
    ) {
        try {
            UUID technicalId = UUID.fromString(technicalIdStr);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId format for delete: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when deleting subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity (no business logic)
    private Subscriber mapToEntity(SubscriberRequest request) {
        Subscriber entity = new Subscriber();
        entity.setId(request.getId());
        entity.setName(request.getName());
        entity.setActive(request.getActive());
        entity.setCreatedAt(request.getCreatedAt());

        if (request.getContact() != null) {
            Subscriber.Contact contact = new Subscriber.Contact();
            contact.setEmail(request.getContact().getEmail());
            entity.setContact(contact);
        }

        entity.setSubscribedCategories(request.getSubscribedCategories());

        if (request.getSubscribedYearRange() != null) {
            Subscriber.YearRange yr = new Subscriber.YearRange();
            yr.setFrom(request.getSubscribedYearRange().getFrom());
            yr.setTo(request.getSubscribedYearRange().getTo());
            entity.setSubscribedYearRange(yr);
        }
        return entity;
    }

    // Static DTO classes

    @Data
    @Schema(name = "SubscriberRequest", description = "Subscriber request payload")
    public static class SubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-42")
        private String id;

        @Schema(description = "Subscriber name", example = "Alice")
        private String name;

        @Schema(description = "Contact information")
        private ContactDto contact;

        @Schema(description = "Subscribed categories")
        private List<String> subscribedCategories;

        @Schema(description = "Subscribed year range")
        private YearRangeDto subscribedYearRange;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Created timestamp (ISO-8601)", example = "2025-08-26T11:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing the technical id")
    public static class CreateResponse {
        @Schema(description = "Technical id generated by the datastore")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkCreateResponse", description = "Response containing technical ids for bulk create")
    public static class BulkCreateResponse {
        @Schema(description = "List of technical ids generated by the datastore")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response for delete operation")
    public static class DeleteResponse {
        @Schema(description = "Technical id of deleted entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        @Schema(description = "Business id of the subscriber")
        private String id;

        @Schema(description = "Subscriber name")
        private String name;

        @Schema(description = "Contact information")
        private ContactDto contact;

        @Schema(description = "Subscribed categories")
        private List<String> subscribedCategories;

        @Schema(description = "Subscribed year range")
        private YearRangeDto subscribedYearRange;

        @Schema(description = "Active flag")
        private Boolean active;

        @Schema(description = "Created timestamp (ISO-8601)")
        private String createdAt;
    }

    @Data
    @Schema(name = "ContactDto", description = "Contact DTO")
    public static class ContactDto {
        @Schema(description = "Email address", example = "alice@example.com")
        private String email;
    }

    @Data
    @Schema(name = "YearRangeDto", description = "Year range DTO")
    public static class YearRangeDto {
        @Schema(description = "From year", example = "1900")
        private String from;

        @Schema(description = "To year", example = "1950")
        private String to;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Search condition for subscribers")
    public static class SearchRequest {
        @Schema(description = "Field name to filter (e.g., id, name, contact.email)", example = "id")
        private String field;

        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare", example = "sub-42")
        private String value;
    }
}