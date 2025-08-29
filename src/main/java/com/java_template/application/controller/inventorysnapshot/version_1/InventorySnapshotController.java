package com.java_template.application.controller.inventorysnapshot.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Tag(name = "InventorySnapshot", description = "API for InventorySnapshot entity (version 1) - proxy to EntityService")
@RestController
@RequestMapping("/api/inventory-snapshots/v1")
public class InventorySnapshotController {
    private static final Logger logger = LoggerFactory.getLogger(InventorySnapshotController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public InventorySnapshotController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Add an InventorySnapshot", description = "Persist a single InventorySnapshot entity. Returns technicalId (UUID).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addInventorySnapshot(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "InventorySnapshot payload", required = true,
                    content = @Content(schema = @Schema(implementation = InventorySnapshotRequest.class)))
            @RequestBody InventorySnapshotRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            InventorySnapshot entity = toEntity(request);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addInventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Add multiple InventorySnapshots", description = "Persist multiple InventorySnapshot entities. Returns list of technicalIds.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddBatchResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addInventorySnapshotsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of InventorySnapshot payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = InventorySnapshotRequest.class))))
            @RequestBody List<InventorySnapshotRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");

            List<InventorySnapshot> entities = new ArrayList<>();
            for (InventorySnapshotRequest req : requests) {
                entities.add(toEntity(req));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            AddBatchResponse resp = new AddBatchResponse();
            List<String> techIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) techIds.add(u.toString());
            }
            resp.setTechnicalIds(techIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addInventorySnapshotsBatch", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding InventorySnapshots batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding InventorySnapshots batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get InventorySnapshot by technicalId", description = "Retrieve a stored InventorySnapshot by technical UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = InventorySnapshotResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getInventorySnapshotById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("InventorySnapshot not found");
            }

            InventorySnapshotResponse resp = objectMapper.treeToValue(dataPayload.getData(), InventorySnapshotResponse.class);
            // attempt to extract technicalId from meta if present
            try {
                JsonNode meta = dataPayload.getMeta();
                if (meta != null && meta.has("entityId")) {
                    resp.setTechnicalId(meta.get("entityId").asText());
                } else {
                    resp.setTechnicalId(technicalId);
                }
            } catch (Exception ex) {
                resp.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument for getInventorySnapshotById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "List InventorySnapshots", description = "Retrieve all InventorySnapshot entities (pageSize/pageNumber not implemented - returns all available).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = InventorySnapshotResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listInventorySnapshots() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<InventorySnapshotResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload.getData() == null) continue;
                    InventorySnapshotResponse resp = objectMapper.treeToValue(payload.getData(), InventorySnapshotResponse.class);
                    try {
                        JsonNode meta = payload.getMeta();
                        if (meta != null && meta.has("entityId")) {
                            resp.setTechnicalId(meta.get("entityId").asText());
                        }
                    } catch (Exception ignored) {}
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while listing InventorySnapshots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while listing InventorySnapshots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Query InventorySnapshots by condition", description = "Query InventorySnapshot entities using a SearchConditionRequest (in-memory filter).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = InventorySnapshotResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/condition")
    public ResponseEntity<?> queryByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchConditionRequest JSON", required = true)
            @RequestBody JsonNode conditionNode) {
        // The controller delegates condition processing to EntityService.getItemsByCondition.
        // Accept raw JsonNode for SearchConditionRequest and pass it through.
        try {
            if (conditionNode == null) throw new IllegalArgumentException("Condition body is required");

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    conditionNode,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<InventorySnapshotResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload.getData() == null) continue;
                    InventorySnapshotResponse resp = objectMapper.treeToValue(payload.getData(), InventorySnapshotResponse.class);
                    try {
                        JsonNode meta = payload.getMeta();
                        if (meta != null && meta.has("entityId")) {
                            resp.setTechnicalId(meta.get("entityId").asText());
                        }
                    } catch (Exception ignored) {}
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for queryByCondition", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while querying InventorySnapshots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while querying InventorySnapshots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Update an InventorySnapshot", description = "Update a single InventorySnapshot by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateInventorySnapshot(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "InventorySnapshot payload", required = true,
                    content = @Content(schema = @Schema(implementation = InventorySnapshotRequest.class)))
            @RequestBody InventorySnapshotRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            InventorySnapshot entity = toEntity(request);

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateInventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while updating InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Delete an InventorySnapshot", description = "Delete a single InventorySnapshot by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteInventorySnapshot(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for deleteInventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting InventorySnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    private ResponseEntity<String> handleExecutionException(Throwable cause) {
        if (cause == null) {
            logger.error("ExecutionException with null cause");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Execution failed");
        }
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument from execution: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        }
        logger.error("Unhandled execution exception", cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Execution failed: " + cause.getMessage());
    }

    private InventorySnapshot toEntity(InventorySnapshotRequest req) {
        InventorySnapshot e = new InventorySnapshot();
        e.setSnapshotId(req.getSnapshotId());
        e.setProductId(req.getProductId());
        e.setSnapshotAt(req.getSnapshotAt());
        e.setStockLevel(req.getStockLevel());
        e.setRestockThreshold(req.getRestockThreshold());
        return e;
    }

    @Data
    @Schema(name = "InventorySnapshotRequest", description = "Request payload to create/update InventorySnapshot")
    public static class InventorySnapshotRequest {
        @Schema(description = "Business snapshot identifier", example = "snap-20250825-01")
        private String snapshotId;

        @Schema(description = "Product identifier", example = "p-123")
        private String productId;

        @Schema(description = "ISO-8601 timestamp when snapshot was taken", example = "2025-08-25T09:00:00Z")
        private String snapshotAt;

        @Schema(description = "Current stock level", example = "42")
        private Integer stockLevel;

        @Schema(description = "Threshold for restocking", example = "10")
        private Integer restockThreshold;
    }

    @Data
    @Schema(name = "InventorySnapshotResponse", description = "Response payload for InventorySnapshot retrieval")
    public static class InventorySnapshotResponse {
        @Schema(description = "Technical UUID identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business snapshot identifier", example = "snap-20250825-01")
        private String snapshotId;

        @Schema(description = "Product identifier", example = "p-123")
        private String productId;

        @Schema(description = "ISO-8601 timestamp when snapshot was taken", example = "2025-08-25T09:00:00Z")
        private String snapshotAt;

        @Schema(description = "Current stock level", example = "42")
        private Integer stockLevel;

        @Schema(description = "Threshold for restocking", example = "10")
        private Integer restockThreshold;
    }

    @Data
    @Schema(name = "AddResponse", description = "Response containing technicalId")
    public static class AddResponse {
        @Schema(description = "Technical UUID identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "AddBatchResponse", description = "Response containing list of technicalIds")
    public static class AddBatchResponse {
        @Schema(description = "List of technical UUID identifiers")
        private List<String> technicalIds;
    }
}