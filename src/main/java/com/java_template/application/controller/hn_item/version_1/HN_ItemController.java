package com.java_template.application.controller.hn_item.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.hn_item.version_1.HN_Item;
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
@RequestMapping("/api/hn-item/v1")
@Tag(name = "HN_Item", description = "HN_Item entity operations (v1)")
public class HN_ItemController {

    private static final Logger logger = LoggerFactory.getLogger(HN_ItemController.class);

    private final EntityService entityService;

    public HN_ItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add HN_Item", description = "Adds a single HN_Item entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/item")
    public ResponseEntity<?> addItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "HN_Item to add",
                    required = true,
                    content = @Content(schema = @Schema(implementation = HN_Item.class))
            )
            @RequestBody HN_Item data) {
        try {
            if (data == null) {
                throw new IllegalArgumentException("Request body is null");
            }
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    data
            );

            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to addItem: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addItem", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding item", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in addItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add multiple HN_Items", description = "Adds multiple HN_Item entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items")
    public ResponseEntity<?> addItems(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of HN_Item to add",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = HN_Item.class)))
            )
            @RequestBody List<HN_Item> data) {
        try {
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Request body is null or empty");
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    data
            );

            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to addItems: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addItems", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding items", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in addItems", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get HN_Item by technicalId", description = "Retrieves a single HN_Item by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/item/{technicalId}")
    public ResponseEntity<?> getItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is null or blank");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    uuid
            );

            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(new ItemResponse(item));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to getItem: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItem", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting item", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in getItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all HN_Items", description = "Retrieves all HN_Item entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/items")
    public ResponseEntity<?> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItems", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting items", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in getItems", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get HN_Items by condition", description = "Retrieves HN_Item entities filtered by a search condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ItemsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items/search")
    public ResponseEntity<?> getItemsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition",
                    required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequest.class))
            )
            @RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Search condition is missing");
            }

            // Use provided SearchConditionRequest directly
            SearchConditionRequest condition = request.getCondition();

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to getItemsByCondition: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItemsByCondition", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching items", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in getItemsByCondition", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update HN_Item", description = "Updates an existing HN_Item by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/item/{technicalId}")
    public ResponseEntity<?> updateItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated HN_Item data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = HN_Item.class))
            )
            @RequestBody HN_Item data) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is null or blank");
            }
            if (data == null) {
                throw new IllegalArgumentException("Request body is null");
            }

            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    uuid,
                    data
            );

            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to updateItem: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateItem", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating item", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in updateItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete HN_Item", description = "Deletes an existing HN_Item by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/item/{technicalId}")
    public ResponseEntity<?> deleteItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is null or blank");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    uuid
            );

            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to deleteItem: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteItem", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting item", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Error in deleteItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "IdResponse", description = "Response with single UUID")
    public static class IdResponse {
        @Schema(description = "Technical id of the entity", required = true)
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response with list of UUIDs")
    public static class IdsResponse {
        @Schema(description = "Technical ids of created entities", required = true)
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "ItemResponse", description = "Response with a single item (raw JSON)")
    public static class ItemResponse {
        @Schema(description = "Item as JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode item;

        public ItemResponse(ObjectNode item) {
            this.item = item;
        }
    }

    @Data
    @Schema(name = "ItemsResponse", description = "Response with multiple items (raw JSON)")
    public static class ItemsResponse {
        @Schema(description = "Items as JSON array", required = true, implementation = ArrayNode.class)
        private ArrayNode items;

        public ItemsResponse(ArrayNode items) {
            this.items = items;
        }
    }

    @Data
    @Schema(name = "SearchRequest", description = "Search condition request wrapper")
    public static class SearchRequest {
        @Schema(description = "Search condition", required = true, implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }
}