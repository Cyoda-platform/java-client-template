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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber Controller", description = "Controller proxy for Subscriber entity operations (version 1)")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Register a new Subscriber. Returns technicalId of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Subscriber creation request",
            required = true,
            content = @Content(schema = @Schema(implementation = SubscriberRequest.class),
                    examples = @ExampleObject(value = "{\n  \"subscriberId\": \"sub_42\",\n  \"name\": \"Research Team\",\n  \"contactEndpoint\": \"https://hooks.example.com/notify\",\n  \"filters\": { \"category\": \"physics\" },\n  \"format\": \"summary\"\n}"))
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setName(request.getName());
            entity.setContactEndpoint(request.getContactEndpoint());
            entity.setFormat(request.getFormat());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setStatus(request.getStatus());

            if (request.getFilters() != null) {
                Subscriber.Filters filters = new Subscriber.Filters();
                filters.setCategory(request.getFilters().getCategory());
                filters.setCountry(request.getFilters().getCountry());
                filters.setPrizeYear(request.getFilters().getPrizeYear());
                entity.setFilters(filters);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when creating subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found during create operation: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during create operation: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr) {
        try {
            if (technicalIdStr == null || technicalIdStr.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
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

            Subscriber entity = mapper.treeToValue((JsonNode) node, Subscriber.class);

            SubscriberResponse resp = mapToResponse(entity);

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when retrieving subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during retrieval: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscriber entities (version 1).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );

            ArrayNode arr = itemsFuture.get();
            List<SubscriberResponse> respList = new ArrayList<>();
            if (arr != null) {
                for (JsonNode node : arr) {
                    Subscriber entity = mapper.treeToValue(node, Subscriber.class);
                    respList.add(mapToResponse(entity));
                }
            }
            return ResponseEntity.ok(respList);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during getAll: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting all subscribers", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when getting all subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when getting all subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Subscribers by simple condition", description = "Search Subscribers by a single field condition. Supported operators: EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Simple search request",
            required = true,
            content = @Content(schema = @Schema(implementation = SearchRequest.class),
                    examples = @ExampleObject(value = "{ \"fieldName\": \"subscriberId\", \"operator\": \"EQUALS\", \"value\": \"sub_42\" }"))
    )
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchSubscribers(@RequestBody SearchRequest request) {
        try {
            if (request == null || request.getFieldName() == null || request.getFieldName().isBlank()) {
                throw new IllegalArgumentException("fieldName is required");
            }
            if (request.getOperator() == null || request.getOperator().isBlank()) {
                throw new IllegalArgumentException("operator is required");
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + request.getFieldName(), request.getOperator(), request.getValue())
            );

            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredFuture.get();
            List<SubscriberResponse> respList = new ArrayList<>();
            if (arr != null) {
                for (JsonNode node : arr) {
                    Subscriber entity = mapper.treeToValue(node, Subscriber.class);
                    respList.add(mapToResponse(entity));
                }
            }
            return ResponseEntity.ok(respList);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when searching subscribers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during search: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching subscribers", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when searching subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when searching subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing Subscriber by technicalId. Returns technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Subscriber update request",
            required = true,
            content = @Content(schema = @Schema(implementation = SubscriberRequest.class))
    )
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr,
            @RequestBody SubscriberRequest request) {
        try {
            if (technicalIdStr == null || technicalIdStr.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }
            UUID technicalId = UUID.fromString(technicalIdStr);

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setName(request.getName());
            entity.setContactEndpoint(request.getContactEndpoint());
            entity.setFormat(request.getFormat());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setStatus(request.getStatus());

            if (request.getFilters() != null) {
                Subscriber.Filters filters = new Subscriber.Filters();
                filters.setCategory(request.getFilters().getCategory());
                filters.setCountry(request.getFilters().getCountry());
                filters.setPrizeYear(request.getFilters().getPrizeYear());
                entity.setFilters(filters);
            }

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId,
                    entity
            );

            UUID updatedId = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when updating subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found during update: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during update: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when updating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when updating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by its technicalId. Returns technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr) {
        try {
            if (technicalIdStr == null || technicalIdStr.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID technicalId = UUID.fromString(technicalIdStr);

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId
            );

            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when deleting subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found during delete: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during delete: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when deleting subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when deleting subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Subscribers", description = "Register multiple Subscribers in a batch. Returns technicalIds of created entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Batch subscriber creation request",
            required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberRequest.class)))
    )
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscribersBatch(@RequestBody List<SubscriberRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body is missing or empty");
            }

            List<Subscriber> entities = new ArrayList<>();
            for (SubscriberRequest request : requests) {
                Subscriber entity = new Subscriber();
                entity.setSubscriberId(request.getSubscriberId());
                entity.setName(request.getName());
                entity.setContactEndpoint(request.getContactEndpoint());
                entity.setFormat(request.getFormat());
                entity.setCreatedAt(request.getCreatedAt());
                entity.setStatus(request.getStatus());
                if (request.getFilters() != null) {
                    Subscriber.Filters filters = new Subscriber.Filters();
                    filters.setCategory(request.getFilters().getCategory());
                    filters.setCountry(request.getFilters().getCountry());
                    filters.setPrizeYear(request.getFilters().getPrizeYear());
                    entity.setFilters(filters);
                }
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            List<String> idStrings = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) {
                    idStrings.add(id.toString());
                }
            }
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.setTechnicalIds(idStrings);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when creating subscribers batch: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during batch create: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscribers batch", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating subscribers batch", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating subscribers batch", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Helper mapper
    private SubscriberResponse mapToResponse(Subscriber entity) {
        SubscriberResponse resp = new SubscriberResponse();
        resp.setSubscriberId(entity.getSubscriberId());
        resp.setName(entity.getName());
        resp.setContactEndpoint(entity.getContactEndpoint());
        resp.setFormat(entity.getFormat());
        resp.setStatus(entity.getStatus());
        resp.setCreatedAt(entity.getCreatedAt());
        if (entity.getFilters() != null) {
            SubscriberResponse.FiltersResponse fr = new SubscriberResponse.FiltersResponse();
            fr.setCategory(entity.getFilters().getCategory());
            fr.setCountry(entity.getFilters().getCountry());
            fr.setPrizeYear(entity.getFilters().getPrizeYear());
            resp.setFilters(fr);
        }
        return resp;
    }

    // Static DTOs for request/response

    @Schema(name = "SubscriberRequest", description = "Payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Business identifier for the subscriber", example = "sub_42")
        private String subscriberId;

        @Schema(description = "Display name", example = "Research Team")
        private String name;

        @Schema(description = "Contact endpoint (email or webhook URL)", example = "https://hooks.example.com/notify")
        private String contactEndpoint;

        @Schema(description = "Filter criteria", implementation = SubscriberRequest.FiltersRequest.class)
        private FiltersRequest filters;

        @Schema(description = "Format of notifications", example = "summary")
        private String format;

        @Schema(description = "Creation timestamp (ISO-8601)", example = "2025-01-01T00:00:00Z")
        private String createdAt;

        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContactEndpoint() {
            return contactEndpoint;
        }

        public void setContactEndpoint(String contactEndpoint) {
            this.contactEndpoint = contactEndpoint;
        }

        public FiltersRequest getFilters() {
            return filters;
        }

        public void setFilters(FiltersRequest filters) {
            this.filters = filters;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Schema(name = "FiltersRequest", description = "Filter object for subscriber")
        public static class FiltersRequest {
            @Schema(description = "Category filter", example = "physics")
            private String category;

            @Schema(description = "Country filter", example = "US")
            private String country;

            @Schema(description = "Prize year filter", example = "2024")
            private Integer prizeYear;

            public String getCategory() {
                return category;
            }

            public void setCategory(String category) {
                this.category = category;
            }

            public String getCountry() {
                return country;
            }

            public void setCountry(String country) {
                this.country = country;
            }

            public Integer getPrizeYear() {
                return prizeYear;
            }

            public void setPrizeYear(Integer prizeYear) {
                this.prizeYear = prizeYear;
            }
        }
    }

    @Schema(name = "SearchRequest", description = "Simple search request for Subscribers")
    public static class SearchRequest {
        @Schema(description = "Field name to search on (without $. prefix)", example = "subscriberId")
        private String fieldName;

        @Schema(description = "Operator to use (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, ...)", example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare to", example = "sub_42")
        private String value;

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of created entity", example = "tch_sub_987")
        private String technicalId;

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Schema(name = "TechnicalIdsResponse", description = "Response containing technical ids for batch operations")
    public static class TechnicalIdsResponse {
        @Schema(description = "Technical identifiers of created entities")
        private List<String> technicalIds;

        public List<String> getTechnicalIds() {
            return technicalIds;
        }

        public void setTechnicalIds(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Schema(name = "SubscriberResponse", description = "Subscriber entity response")
    public static class SubscriberResponse {
        @Schema(description = "Business identifier for the subscriber", example = "sub_42")
        private String subscriberId;

        @Schema(description = "Display name", example = "Research Team")
        private String name;

        @Schema(description = "Contact endpoint (email or webhook URL)", example = "https://hooks.example.com/notify")
        private String contactEndpoint;

        @Schema(description = "Filter criteria", implementation = SubscriberResponse.FiltersResponse.class)
        private FiltersResponse filters;

        @Schema(description = "Format of notifications", example = "summary")
        private String format;

        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        @Schema(description = "Creation timestamp (ISO-8601)", example = "2025-01-01T00:00:00Z")
        private String createdAt;

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContactEndpoint() {
            return contactEndpoint;
        }

        public void setContactEndpoint(String contactEndpoint) {
            this.contactEndpoint = contactEndpoint;
        }

        public FiltersResponse getFilters() {
            return filters;
        }

        public void setFilters(FiltersResponse filters) {
            this.filters = filters;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        @Schema(name = "FiltersResponse", description = "Filters for the subscriber")
        public static class FiltersResponse {
            @Schema(description = "Category filter", example = "physics")
            private String category;

            @Schema(description = "Country filter", example = "US")
            private String country;

            @Schema(description = "Prize year filter", example = "2024")
            private Integer prizeYear;

            public String getCategory() {
                return category;
            }

            public void setCategory(String category) {
                this.category = category;
            }

            public String getCountry() {
                return country;
            }

            public void setCountry(String country) {
                this.country = country;
            }

            public Integer getPrizeYear() {
                return prizeYear;
            }

            public void setPrizeYear(Integer prizeYear) {
                this.prizeYear = prizeYear;
            }
        }
    }
}