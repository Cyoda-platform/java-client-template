package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller that proxies requests to EntityService for Subscriber entity (version 1).
 *
 * Notes:
 * - No business logic implemented in controller; only proxies to EntityService.
 * - DTOs for request/response are static classes defined below with Swagger @Schema annotations.
 */
@RestController
@RequestMapping("/api/subscriber/v1")
@Tag(name = "Subscriber", description = "Subscriber entity API (v1)")
public class SubscriberController {

    private final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create subscriber", description = "Adds a single Subscriber entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addSubscriber(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber payload")
        @RequestBody CreateSubscriberRequest request
    ) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while adding subscriber", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while adding subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create subscribers (batch)", description = "Adds multiple Subscriber entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addSubscribers(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of subscribers")
        @RequestBody CreateSubscribersRequest request
    ) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                request.getData()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while adding subscribers", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while adding subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get subscriber by ID", description = "Retrieves a Subscriber entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getSubscriberById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(new SubscriberResponse(item));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while retrieving subscriber", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while retrieving subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all subscribers", description = "Retrieves all Subscriber entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(new SubscribersResponse(items));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while retrieving subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter subscribers", description = "Retrieves Subscriber entities filtered by conditions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/filter", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> filterSubscribers(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Filter request")
        @RequestBody FilterRequest request
    ) {
        try {
            // Build conditions from request criteria
            List<Condition> conditions = new ArrayList<>();
            if (request.getCriteria() != null) {
                for (FilterCriterion c : request.getCriteria()) {
                    String jsonPath = c.getJsonPath();
                    String operator = c.getOperator();
                    String value = c.getValue();
                    conditions.add(Condition.of(jsonPath, operator, value));
                }
            }
            SearchConditionRequest condition;
            if (conditions.isEmpty()) {
                // Use empty group to retrieve all
                condition = SearchConditionRequest.group("AND");
            } else {
                condition = SearchConditionRequest.group(
                    request.getGroupOperator() == null ? "AND" : request.getGroupOperator(),
                    conditions.toArray(new Condition[0])
                );
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(new SubscribersResponse(items));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while filtering subscribers", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while filtering subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while filtering subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update subscriber", description = "Updates a Subscriber entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateSubscriber(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated subscriber payload")
        @RequestBody UpdateSubscriberRequest request
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid,
                request.getData()
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while updating subscriber", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete subscriber", description = "Deletes a Subscriber entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> deleteSubscriber(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument while deleting subscriber", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        }
    }

    // ----------------------------
    // DTOs
    // ----------------------------

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request to create a single subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Subscriber body as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "CreateSubscribersRequest", description = "Request to create multiple subscribers")
    public static class CreateSubscribersRequest {
        @Schema(description = "List of subscriber JSON objects", required = true)
        private List<ObjectNode> data;
    }

    @Data
    @Schema(name = "UpdateSubscriberRequest", description = "Request to update a subscriber")
    public static class UpdateSubscriberRequest {
        @Schema(description = "Subscriber body as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "FilterCriterion", description = "Single filter criterion")
    public static class FilterCriterion {
        @Schema(description = "JSON path to the field (e.g. $.fieldName)", required = true, example = "$.email")
        private String jsonPath;
        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", required = true, example = "EQUALS")
        private String operator;
        @Schema(description = "Value to compare to", required = true, example = "test@example.com")
        private String value;
    }

    @Data
    @Schema(name = "FilterRequest", description = "Request for filtering subscribers")
    public static class FilterRequest {
        @Schema(description = "Grouping operator for criteria (AND/OR)", example = "AND")
        private String groupOperator;
        @Schema(description = "List of filter criteria")
        private List<FilterCriterion> criteria;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing single UUID")
    public static class IdResponse {
        @Schema(description = "Technical id", required = true)
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing list of UUIDs")
    public static class IdsResponse {
        @Schema(description = "List of technical ids", required = true)
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Response wrapping a subscriber JSON object")
    public static class SubscriberResponse {
        @Schema(description = "Subscriber JSON object", required = true)
        private ObjectNode data;

        public SubscriberResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "SubscribersResponse", description = "Response wrapping an array of subscriber JSON objects")
    public static class SubscribersResponse {
        @Schema(description = "Array of subscriber JSON objects", required = true)
        private ArrayNode data;

        public SubscribersResponse(ArrayNode data) {
            this.data = data;
        }
    }
}