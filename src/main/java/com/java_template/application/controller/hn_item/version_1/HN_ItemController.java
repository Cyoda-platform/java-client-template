package com.java_template.application.controller.hn_item.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hn_item.version_1.HN_Item;
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
 * Dull proxy controller for HN_Item entity. All business logic implemented in workflows/workers.
 */
@RestController
@RequestMapping("/api/hn-item/v1")
@Tag(name = "HN_Item", description = "Proxy endpoints for HN_Item entity (version 1)")
public class HN_ItemController {

    private static final Logger logger = LoggerFactory.getLogger(HN_ItemController.class);

    private final EntityService entityService;

    public HN_ItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create HN_Item", description = "Proxy to entity service to create a single HN_Item. Returns technicalId only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "HN_Item payload")
            @RequestBody HN_Item item) {
        try {
            if (item == null) {
                throw new IllegalArgumentException("Request body must not be null");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    item
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to addItem", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple HN_Item", description = "Proxy to entity service to create multiple HN_Item entities. Returns list of technicalIds.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addItems(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of HN_Item payloads")
            @RequestBody List<HN_Item> items) {
        try {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one item");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    items
            );
            List<UUID> ids = idsFuture.get();
            TechnicalIdListResponse resp = new TechnicalIdListResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to addItems", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addItems", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HN_Item by technicalId", description = "Retrieve a HN_Item by its technical UUID assigned by the system.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getItemByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getItemByTechnicalId", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItemByTechnicalId", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItemByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all HN_Item entities", description = "Retrieve all HN_Item entities.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getItems", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter HN_Item entities", description = "Retrieve HN_Item entities by a provided search condition (in-memory).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> getItemsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition")
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Condition must not be null");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getItemsByCondition", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItemsByCondition", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering items", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItemsByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update HN_Item", description = "Proxy to entity service to update a HN_Item by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "HN_Item payload")
            @RequestBody HN_Item item) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            if (item == null) {
                throw new IllegalArgumentException("Request body must not be null");
            }
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    item
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updateItem", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete HN_Item", description = "Proxy to entity service to delete a HN_Item by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in deleteItem", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting item", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HN item by its HN id", description = "Retrieve the stored original HN JSON by the HN id field (not technicalId). Returns the stored rawJson including importTimestamp.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HNItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/by-id/{id}")
    public ResponseEntity<?> getItemByHNId(
            @Parameter(name = "id", description = "HN id (the id field inside the HN_Item payload)")
            @PathVariable("id") String id) {
        try {
            if (id == null) {
                throw new IllegalArgumentException("id must be provided");
            }
            // Build simple condition: $.id EQUALS id
            SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.id", "EQUALS", id)
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    HN_Item.ENTITY_NAME,
                    String.valueOf(HN_Item.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode results = filteredItemsFuture.get();
            if (results == null || results.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HN item not found for id: " + id);
            }
            ObjectNode first = (ObjectNode) results.get(0);
            // If the stored entity has a rawJson field, return that rawJson; otherwise return the full node.
            if (first.has("rawJson")) {
                ObjectNode rawJson = (ObjectNode) first.get("rawJson");
                HNItemResponse resp = new HNItemResponse();
                resp.setRawJson(rawJson);
                return ResponseEntity.ok(resp);
            } else {
                HNItemResponse resp = new HNItemResponse();
                resp.setRawJson(first);
                return ResponseEntity.ok(resp);
            }
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getItemByHNId", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getItemByHNId", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving HN item by id", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getItemByHNId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs used for request/response payloads

    @Data
    @Schema(description = "Response containing a technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID assigned to the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(description = "Response containing a list of technicalIds")
    public static class TechnicalIdListResponse {
        @Schema(description = "List of technical UUIDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(description = "Response wrapping the original raw HN JSON stored for the item")
    public static class HNItemResponse {
        @Schema(description = "Original raw JSON as stored, including importTimestamp")
        private ObjectNode rawJson;
    }
}