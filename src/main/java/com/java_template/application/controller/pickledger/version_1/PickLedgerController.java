package com.java_template.application.controller.pickledger.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
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

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

/**
 * Dull proxy controller for PickLedger entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/api/v1/pickledger")
@Tag(name = "PickLedger", description = "APIs to create and retrieve PickLedger entities (event-driven).")
public class PickLedgerController {

    private static final Logger logger = LoggerFactory.getLogger(PickLedgerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PickLedgerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create PickLedger", description = "Create a PickLedger entity (event). Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPickLedger(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "PickLedger create request",
            content = @Content(schema = @Schema(implementation = CreatePickLedgerRequest.class))
    ) @RequestBody CreatePickLedgerRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            PickLedger entity = new PickLedger();
            // generate natural id for entity to satisfy entity.isValid()
            entity.setId(UUID.randomUUID().toString());
            entity.setShipmentId(request.getShipmentId());
            entity.setOrderId(request.getOrderId());
            entity.setProductId(request.getProductId());
            entity.setQtyRequested(request.getQtyRequested());
            entity.setQtyPicked(request.getQtyPicked());
            entity.setAuditorId(request.getAuditorId());
            entity.setAuditStatus(request.getAuditStatus());
            entity.setTimestamp(request.getTimestamp());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    PickLedger.ENTITY_NAME,
                    PickLedger.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createPickLedger: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPickLedger", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createPickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple PickLedger items", description = "Create multiple PickLedger entities in batch. Returns technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPickLedgersBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of PickLedger create requests",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreatePickLedgerRequest.class)))
    ) @RequestBody List<CreatePickLedgerRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");

            List<PickLedger> entities = new ArrayList<>();
            for (CreatePickLedgerRequest req : requests) {
                PickLedger entity = new PickLedger();
                entity.setId(UUID.randomUUID().toString());
                entity.setShipmentId(req.getShipmentId());
                entity.setOrderId(req.getOrderId());
                entity.setProductId(req.getProductId());
                entity.setQtyRequested(req.getQtyRequested());
                entity.setQtyPicked(req.getQtyPicked());
                entity.setAuditorId(req.getAuditorId());
                entity.setAuditStatus(req.getAuditStatus());
                entity.setTimestamp(req.getTimestamp());
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    PickLedger.ENTITY_NAME,
                    PickLedger.ENTITY_VERSION,
                    entities
            );
            List<UUID> technicalIds = idsFuture.get();
            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            List<String> ids = new ArrayList<>();
            if (technicalIds != null) {
                for (UUID u : technicalIds) ids.add(u.toString());
            }
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createPickLedgersBatch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPickLedgersBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createPickLedgersBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get PickLedger by technicalId", description = "Retrieve a PickLedger by technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PickLedgerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPickLedgerById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PickLedger not found for technicalId: " + technicalId);
            }
            PickLedgerResponse resp = objectMapper.treeToValue((JsonNode) node, PickLedgerResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getPickLedgerById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPickLedgerById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getPickLedgerById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all PickLedger items", description = "Retrieve all PickLedger items (unpaged).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PickLedgerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPickLedgers() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    PickLedger.ENTITY_NAME,
                    PickLedger.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<PickLedgerResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null) {
                        PickLedgerResponse resp = objectMapper.treeToValue(data, PickLedgerResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllPickLedgers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllPickLedgers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query PickLedger by condition", description = "Query PickLedger items by search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PickLedgerResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/query")
    public ResponseEntity<?> queryPickLedgers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "SearchConditionRequest body",
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class))
            ) @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("SearchConditionRequest is required");

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    PickLedger.ENTITY_NAME,
                    PickLedger.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<PickLedgerResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null) {
                        PickLedgerResponse resp = objectMapper.treeToValue(data, PickLedgerResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for queryPickLedgers: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in queryPickLedgers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in queryPickLedgers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update PickLedger by technicalId", description = "Update a PickLedger entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePickLedger(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PickLedger update request",
                    content = @Content(schema = @Schema(implementation = UpdatePickLedgerRequest.class))
            ) @RequestBody UpdatePickLedgerRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            PickLedger entity = new PickLedger();
            // prefer provided natural id if present; otherwise require it to be present in the request to satisfy entity.isValid()
            entity.setId(request.getId());
            entity.setShipmentId(request.getShipmentId());
            entity.setOrderId(request.getOrderId());
            entity.setProductId(request.getProductId());
            entity.setQtyRequested(request.getQtyRequested());
            entity.setQtyPicked(request.getQtyPicked());
            entity.setAuditorId(request.getAuditorId());
            entity.setAuditStatus(request.getAuditStatus());
            entity.setTimestamp(request.getTimestamp());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updatePickLedger: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePickLedger", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updatePickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete PickLedger by technicalId", description = "Delete a PickLedger entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePickLedger(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for deletePickLedger: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePickLedger", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deletePickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTO classes ---

    @Data
    @Schema(name = "CreatePickLedgerRequest", description = "Request payload to create a PickLedger")
    public static class CreatePickLedgerRequest {
        @Schema(description = "Shipment ID (natural id string)", example = "shipment-123")
        private String shipmentId;

        @Schema(description = "Order ID (natural id string)", example = "order-123")
        private String orderId;

        @Schema(description = "Product ID (natural id string)", example = "product-123")
        private String productId;

        @Schema(description = "Quantity requested", example = "5")
        private Integer qtyRequested;

        @Schema(description = "Quantity picked", example = "5")
        private Integer qtyPicked;

        @Schema(description = "Auditor ID (optional)", example = "auditor-uuid")
        private String auditorId;

        @Schema(description = "Audit status (AUDIT_PENDING, AUDIT_PASSED, AUDIT_FAILED)", example = "AUDIT_PENDING")
        private String auditStatus;

        @Schema(description = "Timestamp ISO-8601", example = "2025-08-28T12:00:10Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "UpdatePickLedgerRequest", description = "Request payload to update a PickLedger")
    public static class UpdatePickLedgerRequest {
        @Schema(description = "Natural entity ID (required by entity validation)", example = "pickledger-123")
        private String id;

        @Schema(description = "Shipment ID (natural id string)", example = "shipment-123")
        private String shipmentId;

        @Schema(description = "Order ID (natural id string)", example = "order-123")
        private String orderId;

        @Schema(description = "Product ID (natural id string)", example = "product-123")
        private String productId;

        @Schema(description = "Quantity requested", example = "5")
        private Integer qtyRequested;

        @Schema(description = "Quantity picked", example = "5")
        private Integer qtyPicked;

        @Schema(description = "Auditor ID (optional)", example = "auditor-uuid")
        private String auditorId;

        @Schema(description = "Audit status (AUDIT_PENDING, AUDIT_PASSED, AUDIT_FAILED)", example = "AUDIT_PENDING")
        private String auditStatus;

        @Schema(description = "Timestamp ISO-8601", example = "2025-08-28T12:00:10Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "PickLedgerResponse", description = "PickLedger entity representation returned from storage")
    public static class PickLedgerResponse {
        @Schema(description = "Natural entity ID", example = "pickledger-123")
        private String id;

        @Schema(description = "Audit status", example = "AUDIT_PENDING")
        private String auditStatus;

        @Schema(description = "Auditor ID", example = "auditor-uuid")
        private String auditorId;

        @Schema(description = "Order ID", example = "order-123")
        private String orderId;

        @Schema(description = "Product ID", example = "product-123")
        private String productId;

        @Schema(description = "Quantity picked", example = "5")
        private Integer qtyPicked;

        @Schema(description = "Quantity requested", example = "5")
        private Integer qtyRequested;

        @Schema(description = "Shipment ID", example = "shipment-123")
        private String shipmentId;

        @Schema(description = "Timestamp ISO-8601", example = "2025-08-28T12:00:10Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId assigned by the system")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Response containing multiple technicalIds")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids (UUIDs)")
        private List<String> technicalIds;
    }
}