package com.java_template.application.controller.storeditem.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.storeditem.version_1.StoredItem;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/stored-item/v1")
@Tag(name = "StoredItem", description = "API for StoredItem entity operations")
public class StoredItemController {

    private static final Logger logger = LoggerFactory.getLogger(StoredItemController.class);

    private final EntityService entityService;

    public StoredItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add StoredItem", description = "Adds a single StoredItem entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "StoredItem payload", required = true,
                    content = @Content(schema = @Schema(implementation = ObjectNode.class)))
            @RequestBody ObjectNode data) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addItem", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add multiple StoredItems", description = "Adds multiple StoredItem entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addItems(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of StoredItem payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
            @RequestBody ArrayNode data) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    data
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addItems", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get StoredItem by technicalId", description = "Retrieves a single StoredItem by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(new ItemResponse(item));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid id format for getItem", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while getting item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all StoredItems", description = "Retrieves all StoredItem entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while getting items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter StoredItems", description = "Retrieves StoredItem entities by given search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> getItemsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while searching items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItemsByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update StoredItem", description = "Updates a StoredItem by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "StoredItem payload for update", required = true,
                    content = @Content(schema = @Schema(implementation = ObjectNode.class)))
            @RequestBody ObjectNode data) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    id,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateItem", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete StoredItem", description = "Deletes a StoredItem by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    StoredItem.ENTITY_NAME,
                    String.valueOf(StoredItem.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid id format for deleteItem", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionExceptionCause(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionExceptionCause(Throwable cause) {
        if (cause == null) {
            logger.error("ExecutionException with null cause");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown error");
        }
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Illegal argument in execution", cause);
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution failed", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        }
    }

    // DTOs used for requests/responses

    @Data
    @Schema(name = "IdResponse", description = "Response containing single UUID")
    public static class IdResponse {
        @Schema(description = "Technical id", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
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
    @Schema(name = "ItemResponse", description = "Response containing single entity as ObjectNode")
    public static class ItemResponse {
        @Schema(description = "Entity data", required = true)
        private ObjectNode item;

        public ItemResponse(ObjectNode item) {
            this.item = item;
        }
    }

    @Data
    @Schema(name = "ItemsResponse", description = "Response containing list/array of entities")
    public static class ItemsResponse {
        @Schema(description = "Entities array", required = true)
        private ArrayNode items;

        public ItemsResponse(ArrayNode items) {
            this.items = items;
        }
    }
}