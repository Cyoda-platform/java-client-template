package com.java_template.application.controller.storeditem.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * Dull proxy controller for StoredItem entity. All business logic lives in workflows.
 */
@RestController
@RequestMapping("/api/stored-items/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "StoredItem", description = "StoredItem entity proxy endpoints")
public class StoredItemController {

    private static final Logger logger = LoggerFactory.getLogger(StoredItemController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoredItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create StoredItem", description = "Persist a StoredItem. Returns the created technicalId (UUID string). Business logic is executed asynchronously via workflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateStoredItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createStoredItem(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "StoredItem payload", required = true, content = @Content(schema = @Schema(implementation = CreateStoredItemRequest.class)))
        @RequestBody CreateStoredItemRequest request
    ) {
        try {
            ObjectNode node = objectMapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                node
            );
            UUID id = idFuture.get();
            CreateStoredItemResponse resp = new CreateStoredItemResponse(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument creating stored item", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error creating stored item", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted creating stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple StoredItems", description = "Persist multiple StoredItems in bulk. Returns the list of created technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BulkCreateStoredItemResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createStoredItemsBulk(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of StoredItem payloads", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateStoredItemRequest.class))))
        @RequestBody List<CreateStoredItemRequest> requests
    ) {
        try {
            ArrayNode arrayNode = objectMapper.valueToTree(requests);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                arrayNode
            );
            List<UUID> ids = idsFuture.get();
            BulkCreateStoredItemResponse resp = new BulkCreateStoredItemResponse(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in bulk create", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error in bulk create", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted bulk create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in bulk create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get StoredItem by technicalId", description = "Retrieve a StoredItem by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = StoredItemGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getStoredItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getStoredItem", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error getting stored item", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted getting stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all StoredItems", description = "Retrieve all StoredItems.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StoredItemGetResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getStoredItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error getting stored items", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted getting stored items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting stored items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search StoredItems by condition", description = "Retrieve StoredItems filtered by a SearchConditionRequest. For simple field-based queries use SearchConditionRequest.group and Condition.of.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StoredItemGetResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchStoredItems(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true, content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @RequestBody SearchConditionRequest condition
    ) {
        try {
            ArrayNode items = entityService.getItemsByCondition(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                condition,
                true
            ).get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error searching stored items", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted searching stored items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error searching stored items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update StoredItem", description = "Update an existing StoredItem by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateStoredItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateStoredItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "StoredItem payload", required = true, content = @Content(schema = @Schema(implementation = CreateStoredItemRequest.class)))
        @RequestBody CreateStoredItemRequest request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                id,
                node
            );
            UUID updatedId = updatedFuture.get();
            UpdateStoredItemResponse resp = new UpdateStoredItemResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument updating stored item", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error updating stored item", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted updating stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete StoredItem", description = "Delete a StoredItem by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = DeleteStoredItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteStoredItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedFuture.get();
            DeleteStoredItemResponse resp = new DeleteStoredItemResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId deleting stored item", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error deleting stored item", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted deleting stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting stored item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateStoredItemRequest", description = "Request payload to create a StoredItem")
    public static class CreateStoredItemRequest {
        @Schema(description = "HackerNews item payload (full JSON)", required = true)
        private Object hn_item;

        @Schema(description = "When the item was stored (optional)")
        private String stored_at;

        @Schema(description = "Storage technical id (optional)")
        private String storage_technicalId;

        @Schema(description = "Approximate size in bytes (optional)")
        private Long size_bytes;
    }

    @Data
    @Schema(name = "CreateStoredItemResponse", description = "Response returned when a StoredItem is created")
    public static class CreateStoredItemResponse {
        @Schema(description = "Technical id (UUID string) of created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public CreateStoredItemResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkCreateStoredItemResponse", description = "Response returned when multiple StoredItems are created")
    public static class BulkCreateStoredItemResponse {
        @Schema(description = "List of created technical ids")
        private List<String> technicalIds;

        public BulkCreateStoredItemResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "StoredItemGetResponse", description = "StoredItem returned by GET endpoints")
    public static class StoredItemGetResponse {
        @Schema(description = "Technical id (storage_technicalId) of the item")
        private String technicalId;

        @Schema(description = "When the item was stored")
        private String stored_at;

        @Schema(description = "The HN item payload")
        private Object hn_item;
    }

    @Data
    @Schema(name = "UpdateStoredItemResponse", description = "Response returned when a StoredItem is updated")
    public static class UpdateStoredItemResponse {
        @Schema(description = "Technical id (UUID string) of updated entity")
        private String technicalId;

        public UpdateStoredItemResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteStoredItemResponse", description = "Response returned when a StoredItem is deleted")
    public static class DeleteStoredItemResponse {
        @Schema(description = "Technical id (UUID string) of deleted entity")
        private String technicalId;

        public DeleteStoredItemResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}