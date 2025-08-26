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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriber", description = "Subscriber entity operations (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Create a Subscriber entity and trigger its workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateSubscriberResponse> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactAddress(request.getContactAddress());
            entity.setActive(request.getActive());
            entity.setPreferredPayload(request.getPreferredPayload());

            if (request.getFilters() != null) {
                Subscriber.Filters f = new Subscriber.Filters();
                f.setCategories(request.getFilters().getCategories());
                entity.setFilters(f);
            }

            if (request.getRetryPolicy() != null) {
                Subscriber.RetryPolicy rp = new Subscriber.RetryPolicy();
                rp.setBackoffSeconds(request.getRetryPolicy().getBackoffSeconds());
                rp.setMaxAttempts(request.getRetryPolicy().getMaxAttempts());
                entity.setRetryPolicy(rp);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();

            CreateSubscriberResponse resp = new CreateSubscriberResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Subscribers", description = "Create multiple Subscriber entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateSubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateSubscribersResponse> createSubscribersBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of subscribers to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateSubscriberRequest.class))))
            @RequestBody List<CreateSubscriberRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is required");
            }

            List<Subscriber> entities = requests.stream().map(req -> {
                Subscriber s = new Subscriber();
                s.setId(req.getId());
                s.setName(req.getName());
                s.setContactType(req.getContactType());
                s.setContactAddress(req.getContactAddress());
                s.setActive(req.getActive());
                s.setPreferredPayload(req.getPreferredPayload());
                if (req.getFilters() != null) {
                    Subscriber.Filters f = new Subscriber.Filters();
                    f.setCategories(req.getFilters().getCategories());
                    s.setFilters(f);
                }
                if (req.getRetryPolicy() != null) {
                    Subscriber.RetryPolicy rp = new Subscriber.RetryPolicy();
                    rp.setBackoffSeconds(req.getRetryPolicy().getBackoffSeconds());
                    rp.setMaxAttempts(req.getRetryPolicy().getMaxAttempts());
                    s.setRetryPolicy(rp);
                }
                return s;
            }).collect(Collectors.toList());

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();

            CreateSubscribersResponse resp = new CreateSubscribersResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch create request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while batch creating subscribers", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while batch creating subscribers", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while batch creating subscribers", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<SubscriberResponse> getSubscriberByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID techId = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    techId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }

            SubscriberResponse resp = objectMapper.convertValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get subscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscriber entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<SubscriberResponse>> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            if (array == null) {
                return ResponseEntity.ok(List.of());
            }

            List<SubscriberResponse> list = objectMapper.convertValue(array, new TypeReference<List<SubscriberResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving all subscribers", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all subscribers", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all subscribers", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Filter Subscribers", description = "Filter subscribers by a simple field condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/filter", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SubscriberResponse>> filterSubscribers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Filter request", required = true,
                    content = @Content(schema = @Schema(implementation = FilterRequest.class)))
            @RequestBody FilterRequest request) {
        try {
            if (request == null || request.getField() == null || request.getOperator() == null || request.getValue() == null) {
                throw new IllegalArgumentException("field, operator and value are required");
            }

            Condition cond = Condition.of("$.%s".formatted(request.getField()), request.getOperator(), request.getValue());
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();
            if (array == null) {
                return ResponseEntity.ok(List.of());
            }

            List<SubscriberResponse> list = objectMapper.convertValue(array, new TypeReference<List<SubscriberResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while filtering subscribers", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering subscribers", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while filtering subscribers", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update existing Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UpdateSubscriberResponse> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID techId = UUID.fromString(technicalId);
            Subscriber entity = new Subscriber();
            entity.setTechnicalId(technicalId);
            entity.setId(request.getId());
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactAddress(request.getContactAddress());
            entity.setActive(request.getActive());
            entity.setPreferredPayload(request.getPreferredPayload());

            if (request.getFilters() != null) {
                Subscriber.Filters f = new Subscriber.Filters();
                f.setCategories(request.getFilters().getCategories());
                entity.setFilters(f);
            }

            if (request.getRetryPolicy() != null) {
                Subscriber.RetryPolicy rp = new Subscriber.RetryPolicy();
                rp.setBackoffSeconds(request.getRetryPolicy().getBackoffSeconds());
                rp.setMaxAttempts(request.getRetryPolicy().getMaxAttempts());
                entity.setRetryPolicy(rp);
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    techId,
                    entity
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateSubscriberResponse resp = new UpdateSubscriberResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while updating subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while updating subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<DeleteSubscriberResponse> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID techId = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    techId
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteSubscriberResponse resp = new DeleteSubscriberResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while deleting subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while deleting subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Request and Response DTOs

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-42")
        private String id;

        @Schema(description = "Name of the subscriber", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type (email or webhook)", example = "webhook")
        private String contactType;

        @Schema(description = "Contact address (email or webhook URL)", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Whether subscriber is active", example = "true")
        private Boolean active;

        @Schema(description = "Filters object (optional)")
        private FiltersDto filters;

        @Schema(description = "Preferred payload (summary / full)", example = "summary")
        private String preferredPayload;

        @Schema(description = "Retry policy overrides (optional)")
        private RetryPolicyDto retryPolicy;

        @Data
        @Schema(name = "Filters", description = "Subscriber filters")
        public static class FiltersDto {
            @Schema(description = "Categories filter", example = "[\"Chemistry\",\"Physics\"]")
            private java.util.List<String> categories;
        }

        @Data
        @Schema(name = "RetryPolicy", description = "Retry policy overrides")
        public static class RetryPolicyDto {
            @Schema(description = "Backoff seconds between retries", example = "60")
            private Integer backoffSeconds;

            @Schema(description = "Maximum retry attempts", example = "3")
            private Integer maxAttempts;
        }
    }

    @Data
    @Schema(name = "CreateSubscriberResponse", description = "Response after creating a Subscriber")
    public static class CreateSubscriberResponse {
        @Schema(description = "Technical id assigned to the subscriber", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;
    }

    @Data
    @Schema(name = "CreateSubscribersResponse", description = "Response after creating multiple Subscribers")
    public static class CreateSubscribersResponse {
        @Schema(description = "Technical ids assigned to the subscribers")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber representation returned by GET")
    public static class SubscriberResponse {
        @Schema(description = "Technical id", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;

        @Schema(description = "Business id", example = "sub-42")
        private String id;

        @Schema(description = "Name", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type", example = "webhook")
        private String contactType;

        @Schema(description = "Contact address", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Filters")
        private FiltersDto filters;

        @Schema(description = "Preferred payload", example = "summary")
        private String preferredPayload;

        @Schema(description = "Retry policy")
        private RetryPolicyDto retryPolicy;

        @Data
        @Schema(name = "Filters", description = "Subscriber filters")
        public static class FiltersDto {
            @Schema(description = "Categories filter", example = "[\"Chemistry\",\"Physics\"]")
            private java.util.List<String> categories;
        }

        @Data
        @Schema(name = "RetryPolicy", description = "Retry policy")
        public static class RetryPolicyDto {
            @Schema(description = "Backoff seconds between retries", example = "60")
            private Integer backoffSeconds;

            @Schema(description = "Maximum retry attempts", example = "3")
            private Integer maxAttempts;
        }
    }

    @Data
    @Schema(name = "FilterRequest", description = "Simple filter request to search subscribers by field/operator/value")
    public static class FilterRequest {
        @Schema(description = "Field name to filter on (use entity field name)", example = "id")
        private String field;

        @Schema(description = "Operator e.g. EQUALS, NOT_EQUAL", example = "EQUALS")
        private String operator;

        @Schema(description = "Value to match", example = "sub-42")
        private String value;
    }

    @Data
    @Schema(name = "UpdateSubscriberResponse", description = "Response after updating a Subscriber")
    public static class UpdateSubscriberResponse {
        @Schema(description = "Technical id of the updated subscriber", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteSubscriberResponse", description = "Response after deleting a Subscriber")
    public static class DeleteSubscriberResponse {
        @Schema(description = "Technical id of the deleted subscriber", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;
    }
}