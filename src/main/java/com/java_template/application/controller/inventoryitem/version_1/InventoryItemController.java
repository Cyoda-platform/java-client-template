package com.java_template.application.controller.inventoryitem.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for InventoryItem entity.
 * All business logic is implemented in workflows; this controller simply proxies requests to EntityService.
 */
@RestController
@RequestMapping("/api/v1/inventory-items")
@Tag(name = "InventoryItem Controller", description = "Proxy endpoints for InventoryItem entity (version 1)")
public class InventoryItemController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InventoryItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create InventoryItem", description = "Accepts InventoryItem payload and creates an InventoryItem entity (event-driven). Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createInventoryItem(@org.springframework.web.bind.annotation.RequestBody InventoryItem item) {
        try {
            if (item == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = entityService.addItem(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                item
            ).get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating InventoryItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error creating InventoryItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple InventoryItems", description = "Accepts array of InventoryItem payloads and creates multiple entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdsResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createInventoryItemsBulk(@org.springframework.web.bind.annotation.RequestBody List<InventoryItem> items) {
        try {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one InventoryItem");
            }
            List<java.util.UUID> uuids = entityService.addItems(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                items
            ).get();
            IdsResponse resp = new IdsResponse();
            List<String> ids = new ArrayList<>();
            for (UUID u : uuids) ids.add(u.toString());
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating InventoryItems bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error creating InventoryItems bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get InventoryItem by technicalId", description = "Retrieve InventoryItem by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = InventoryItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getInventoryItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                tid
            ).get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            }
            InventoryItemResponse resp = objectMapper.convertValue(node, InventoryItemResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching InventoryItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error fetching InventoryItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all InventoryItems", description = "Retrieve all InventoryItems")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = InventoryItemResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllInventoryItems() {
        try {
            ArrayNode arr = entityService.getItems(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION)
            ).get();
            List<InventoryItemResponse> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    InventoryItemResponse resp = objectMapper.convertValue(arr.get(i), InventoryItemResponse.class);
                    // Attempt to set technicalId if present in payload
                    if (arr.get(i).has("id")) {
                        resp.setTechnicalId(arr.get(i).get("id").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching InventoryItems", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error fetching InventoryItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search InventoryItems by condition", description = "Search InventoryItems using basic field/operator/value query. Supported operators: EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = InventoryItemResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchInventoryItems(
        @Parameter(description = "Field name to filter (without JSONPath prefix, e.g., id, name, category)") @RequestParam String field,
        @Parameter(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)") @RequestParam String operator,
        @Parameter(description = "Value to compare") @RequestParam String value
    ) {
        try {
            if (field == null || field.isBlank() || operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("field and operator are required");
            }
            String jsonPath = "$." + field;
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of(jsonPath, operator, value)
            );
            ArrayNode arr = entityService.getItemsByCondition(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                condition,
                true
            ).get();
            List<InventoryItemResponse> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    InventoryItemResponse resp = objectMapper.convertValue(arr.get(i), InventoryItemResponse.class);
                    if (arr.get(i).has("id")) {
                        resp.setTechnicalId(arr.get(i).get("id").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching InventoryItems", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error searching InventoryItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update InventoryItem", description = "Update an existing InventoryItem by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateInventoryItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
        @org.springframework.web.bind.annotation.RequestBody InventoryItem item) {
        try {
            if (item == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID tid = UUID.fromString(technicalId);
            UUID updated = entityService.updateItem(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                tid,
                item
            ).get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating InventoryItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error updating InventoryItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete InventoryItem", description = "Delete InventoryItem by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteInventoryItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            UUID deleted = entityService.deleteItem(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION),
                tid
            ).get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting InventoryItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Error deleting InventoryItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request during execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        }
    }

    // Static DTOs for request/response payloads
    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of created/updated/deleted entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID (UUID string)", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing multiple technicalIds")
    public static class IdsResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "InventoryItemResponse", description = "Representation of InventoryItem entity for responses")
    public static class InventoryItemResponse {
        @Schema(description = "Technical ID (external id from inventory source)", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;

        @Schema(description = "Product name")
        private String name;

        @Schema(description = "Classification")
        private String category;

        @Schema(description = "ISO date when added", example = "2025-08-20")
        private String dateAdded;

        @Schema(description = "Storage location")
        private String location;

        @Schema(description = "Unit price")
        private Double price;

        @Schema(description = "On-hand quantity")
        private Integer quantity;

        @Schema(description = "Status (ingested/validated/invalid)")
        private String status;

        @Schema(description = "Supplier name")
        private String supplier;
    }
}