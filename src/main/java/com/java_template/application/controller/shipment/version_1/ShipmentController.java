package com.java_template.application.controller.shipment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.shipment.version_1.Shipment;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/ui/shipment/v1")
@Tag(name = "Shipment API", description = "CRUD proxy endpoints for Shipment entity (version 1)")
@RequiredArgsConstructor
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Shipment", description = "Persist a Shipment entity. Returns technicalId only.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> createShipment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Shipment payload", required = true,
                content = @Content(schema = @Schema(implementation = ShipmentRequest.class)))
            @RequestBody ShipmentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is null");
            Shipment entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createShipment", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createShipment", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Shipments", description = "Persist multiple Shipment entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchTechnicalIdResponse> createShipmentsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Shipments", required = true,
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShipmentRequest.class))))
            @RequestBody List<ShipmentRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is empty");
            List<Shipment> entities = requests.stream().map(this::toEntity).collect(Collectors.toList());
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new BatchTechnicalIdResponse(technicalIds));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid batch request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createShipmentsBatch", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createShipmentsBatch", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Shipment by technicalId", description = "Retrieve a stored Shipment by its technical UUID.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ShipmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShipmentResponse> getShipmentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) throw new IllegalArgumentException("technicalId is null");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) return ResponseEntity.status(404).build();
            JsonNode data = dataPayload.getData();
            ShipmentResponse response = objectMapper.treeToValue(data, ShipmentResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getShipmentById", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getShipmentById", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Shipments", description = "Retrieve all stored Shipments (no pagination).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShipmentResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ShipmentResponse>> listShipments() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<ShipmentResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    ShipmentResponse resp = objectMapper.treeToValue(payload.getData(), ShipmentResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during listShipments", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during listShipments", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Shipments by condition", description = "Retrieve Shipments filtered by SearchConditionRequest.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShipmentResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ShipmentResponse>> searchShipments(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is null");
            CompletableFuture<List<DataPayload>> filteredFuture = entityService.getItemsByCondition(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredFuture.get();
            List<ShipmentResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    ShipmentResponse resp = objectMapper.treeToValue(payload.getData(), ShipmentResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during searchShipments", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during searchShipments", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Shipment", description = "Update a Shipment by technicalId. Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PatchMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> updateShipment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Shipment payload", required = true,
                content = @Content(schema = @Schema(implementation = ShipmentRequest.class)))
            @RequestBody ShipmentRequest request) {
        try {
            if (technicalId == null || request == null) throw new IllegalArgumentException("Invalid input");
            Shipment entity = toEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during updateShipment", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during updateShipment", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Shipment", description = "Delete a Shipment by technicalId. Returns technicalId of deleted entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> deleteShipment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) throw new IllegalArgumentException("technicalId is null");
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delete request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during deleteShipment", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during deleteShipment", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Helper to convert request DTO to entity
    private Shipment toEntity(ShipmentRequest req) {
        Shipment s = new Shipment();
        s.setShipmentId(req.getShipmentId());
        s.setOrderId(req.getOrderId());
        s.setStatus(req.getStatus());
        s.setCreatedAt(req.getCreatedAt());
        s.setUpdatedAt(req.getUpdatedAt());
        if (req.getLines() != null) {
            List<Shipment.ShipmentLine> lines = req.getLines().stream().map(l -> {
                Shipment.ShipmentLine sl = new Shipment.ShipmentLine();
                sl.setSku(l.getSku());
                sl.setQtyOrdered(l.getQtyOrdered());
                sl.setQtyPicked(l.getQtyPicked());
                sl.setQtyShipped(l.getQtyShipped());
                return sl;
            }).collect(Collectors.toList());
            s.setLines(lines);
        } else {
            s.setLines(null);
        }
        return s;
    }

    // Helper DTOs

    @Data
    @Schema(name = "ShipmentRequest", description = "Shipment request payload")
    public static class ShipmentRequest {
        @Schema(description = "Shipment business id", example = "s-123")
        private String shipmentId;
        @Schema(description = "Order business id", example = "o-456")
        private String orderId;
        @Schema(description = "Shipment status", example = "PICKING")
        private String status;
        @Schema(description = "Creation timestamp", example = "2025-01-01T00:00:00Z")
        private String createdAt;
        @Schema(description = "Update timestamp", example = "2025-01-01T01:00:00Z")
        private String updatedAt;
        @Schema(description = "Shipment lines")
        private List<ShipmentLineDto> lines;
    }

    @Data
    @Schema(name = "ShipmentResponse", description = "Shipment response payload")
    public static class ShipmentResponse {
        @Schema(description = "Shipment business id", example = "s-123")
        private String shipmentId;
        @Schema(description = "Order business id", example = "o-456")
        private String orderId;
        @Schema(description = "Shipment status", example = "PICKING")
        private String status;
        @Schema(description = "Creation timestamp", example = "2025-01-01T00:00:00Z")
        private String createdAt;
        @Schema(description = "Update timestamp", example = "2025-01-01T01:00:00Z")
        private String updatedAt;
        @Schema(description = "Shipment lines")
        private List<ShipmentLineDto> lines;
    }

    @Data
    @Schema(name = "ShipmentLineDto", description = "Shipment line DTO")
    public static class ShipmentLineDto {
        @Schema(description = "SKU", example = "SKU-1")
        private String sku;
        @Schema(description = "Quantity ordered", example = "2")
        private Integer qtyOrdered;
        @Schema(description = "Quantity picked", example = "2")
        private Integer qtyPicked;
        @Schema(description = "Quantity shipped", example = "1")
        private Integer qtyShipped;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response with technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
        public TechnicalIdResponse() {}
        public TechnicalIdResponse(String technicalId) { this.technicalId = technicalId; }
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Response with list of technical ids")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
        public BatchTechnicalIdResponse() {}
        public BatchTechnicalIdResponse(List<String> technicalIds) { this.technicalIds = technicalIds; }
    }
}