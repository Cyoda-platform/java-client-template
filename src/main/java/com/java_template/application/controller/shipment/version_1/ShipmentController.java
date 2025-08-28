package com.java_template.application.controller.shipment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/shipment")
@Tag(name = "Shipment", description = "Shipment entity proxy controller (version 1)")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Shipment", description = "Persist a Shipment entity (event). Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createShipment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Shipment payload", required = true,
                    content = @Content(schema = @Schema(implementation = ShipmentRequest.class)))
            @RequestBody ShipmentRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Shipment shipment = mapRequestToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    shipment
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createShipment", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating shipment", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Shipments", description = "Persist multiple Shipment entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createShipments(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of shipments to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShipmentRequest.class))))
            @RequestBody List<ShipmentRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is required and must not be empty");
            }

            List<Object> entities = new ArrayList<>();
            for (ShipmentRequest req : requests) {
                entities.add(mapRequestToEntity(req));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Shipment.ENTITY_NAME,
                    Shipment.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();

            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) stringIds.add(id.toString());
            }
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createShipments", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating shipments", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating shipments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Shipment by technicalId", description = "Retrieve a Shipment by its technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ShipmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getShipmentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Shipment not found");
            }
            ShipmentResponse response = objectMapper.treeToValue(node, ShipmentResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getShipmentById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving shipment", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Shipments", description = "Retrieve all persisted Shipment entities (paged parameters currently ignored)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShipmentResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getShipments() {
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
                    JsonNode data = payload.getData();
                    ShipmentResponse resp = objectMapper.treeToValue(data, ShipmentResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving shipments", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving shipments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Shipment", description = "Update a Shipment by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateShipment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Shipment payload for update", required = true,
                    content = @Content(schema = @Schema(implementation = ShipmentRequest.class)))
            @RequestBody ShipmentRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Shipment shipment = mapRequestToEntity(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), shipment);
            UUID updated = updatedIdFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateShipment", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating shipment", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Shipment", description = "Delete a Shipment by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteShipment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deleted = deletedIdFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteShipment", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting shipment", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper: map request DTO to entity using setters (no business logic)
    private Shipment mapRequestToEntity(ShipmentRequest request) {
        Shipment shipment = new Shipment();
        shipment.setId(request.getId());
        shipment.setShipmentNumber(request.getShipmentNumber());
        shipment.setOrderId(request.getOrderId());
        shipment.setStatus(request.getStatus());
        shipment.setCreatedAt(request.getCreatedAt());
        shipment.setWarehouseId(request.getWarehouseId());
        shipment.setTrackingInfo(request.getTrackingInfo());

        if (request.getItems() != null) {
            List<Shipment.ShipmentItem> items = new ArrayList<>();
            for (ShipmentRequest.ShipmentItemDto dto : request.getItems()) {
                Shipment.ShipmentItem item = new Shipment.ShipmentItem();
                item.setProductId(dto.getProductId());
                item.setQty(dto.getQty());
                items.add(item);
            }
            shipment.setItems(items);
        } else {
            shipment.setItems(null);
        }
        return shipment;
    }

    @Data
    @Schema(name = "ShipmentRequest", description = "Shipment request payload")
    public static class ShipmentRequest {
        private String id;
        private String shipmentNumber;
        private String orderId;
        private String status;
        private String createdAt;
        private List<ShipmentItemDto> items;
        private Map<String, Object> trackingInfo;
        private String warehouseId;

        @Data
        @Schema(name = "ShipmentItemDto", description = "Shipment item")
        public static class ShipmentItemDto {
            private String productId;
            private Integer qty;
        }
    }

    @Data
    @Schema(name = "ShipmentResponse", description = "Shipment response payload")
    public static class ShipmentResponse {
        private String id;
        private String shipmentNumber;
        private String orderId;
        private String status;
        private String createdAt;
        private List<ShipmentItemDto> items;
        private Map<String, Object> trackingInfo;
        private String warehouseId;

        @Data
        @Schema(name = "ShipmentItemDto", description = "Shipment item")
        public static class ShipmentItemDto {
            private String productId;
            private Integer qty;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a single technicalId")
    public static class TechnicalIdResponse {
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        private List<String> technicalIds;
    }
}