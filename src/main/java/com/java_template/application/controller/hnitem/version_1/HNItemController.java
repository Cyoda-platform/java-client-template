package com.java_template.application.controller.hnitem.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

/**
 * Controller for HNItem REST API - version 1
 *
 * Responsibilities:
 * - Proxy requests to EntityService
 * - Validate basic request format
 * - Handle exceptions and return proper HTTP status codes
 *
 * Note: No business logic implemented here.
 */
@RestController
@RequestMapping("/api/hnitem/v1")
@Tag(name = "HNItem", description = "HNItem API")
public class HNItemController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemController.class);

    private final EntityService entityService;

    public HNItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add HNItem", description = "Adds a single HNItem entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items")
    public ResponseEntity<IdResponse> addItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Add HNItem request",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AddItemRequest.class))
            )
            @RequestBody AddItemRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data is required");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in addItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in addItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Add multiple HNItems", description = "Adds multiple HNItem entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items/bulk")
    public ResponseEntity<IdsResponse> addItems(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Add multiple HNItem entities",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddItemsRequest.class)))
            )
            @RequestBody AddItemsRequest request) {
        try {
            if (request == null || request.getEntities() == null) {
                throw new IllegalArgumentException("Entities are required");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    request.getEntities()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addItems: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in addItems", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in addItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get HNItem by technicalId", description = "Retrieves a single HNItem by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/items/{technicalId}")
    public ResponseEntity<ItemResponse> getItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode data = itemFuture.get();
            return ResponseEntity.ok(new ItemResponse(data));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in getItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all HNItems", description = "Retrieves all HNItem entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/items")
    public ResponseEntity<ArrayNode> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                logger.error("Execution error in getItems", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search HNItems by condition", description = "Retrieves HNItems filtered by a given condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items/search")
    public ResponseEntity<ArrayNode> searchItems(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition payload (pass condition JSON node)",
                    required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequest.class))
            )
            @RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    request.getCondition(),
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchItems: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in searchItems", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in searchItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update HNItem", description = "Updates an existing HNItem by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/items/{technicalId}")
    public ResponseEntity<IdResponse> updateItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update HNItem request",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateItemRequest.class))
            )
            @RequestBody UpdateItemRequest request) {
        try {
            if (technicalId == null || request == null || request.getData() == null) {
                throw new IllegalArgumentException("technicalId and data are required");
            }
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request.getData()
            );
            UUID id = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in updateItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in updateItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete HNItem", description = "Deletes an HNItem by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/items/{technicalId}")
    public ResponseEntity<IdResponse> deleteItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error in deleteItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in deleteItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @Schema(name = "AddItemRequest", description = "Request to add a single HNItem")
    public static class AddItemRequest {
        @Schema(description = "HNItem JSON payload", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "AddItemsRequest", description = "Request to add multiple HNItems")
    public static class AddItemsRequest {
        @Schema(description = "Array of HNItem JSON payloads", required = true, implementation = ArrayNode.class)
        private ArrayNode entities;
    }

    @Data
    @Schema(name = "UpdateItemRequest", description = "Request to update HNItem")
    public static class UpdateItemRequest {
        @Schema(description = "HNItem JSON payload", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Search condition payload for HNItem filtering")
    public static class SearchRequest {
        @Schema(description = "Condition JSON node used for filtering (use SearchConditionRequest.group + Condition.of)", required = true, implementation = ObjectNode.class)
        private ObjectNode condition;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing a single UUID")
    public static class IdResponse {
        @Schema(description = "Technical ID", required = true)
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing multiple UUIDs")
    public static class IdsResponse {
        @Schema(description = "List of technical IDs", required = true)
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "ItemResponse", description = "Response containing a single HNItem JSON")
    public static class ItemResponse {
        @Schema(description = "HNItem JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode data;

        public ItemResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "ItemsResponse", description = "Response containing HNItems JSON array")
    public static class ItemsResponse {
        @Schema(description = "HNItems JSON array", required = true, implementation = ArrayNode.class)
        private ArrayNode data;

        public ItemsResponse(ArrayNode data) {
            this.data = data;
        }
    }
}