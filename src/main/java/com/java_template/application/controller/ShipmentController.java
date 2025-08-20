package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.ShipmentItem;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/shipments")
@Tag(name = "Shipment API", description = "APIs for Shipment entity (event-driven)")
public class ShipmentController {
    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Shipment", description = "Persist a Shipment and start its workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createShipment(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Shipment payload") @RequestBody ShipmentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Shipment shipment = new Shipment();
            shipment.setShipmentId(request.getShipmentId());
            shipment.setOrderId(request.getOrderId());
            shipment.setCarrier(request.getCarrier());
            shipment.setTrackingNumber(request.getTrackingNumber());
            shipment.setStatus(request.getStatus());
            shipment.setCreatedAt(request.getCreatedAt());

            if (request.getItems() != null) {
                List<ShipmentItem> items = new ArrayList<>();
                for (ShipmentRequest.ShipmentItemDto it : request.getItems()) {
                    ShipmentItem si = new ShipmentItem();
                    si.setSku(it.getSku());
                    si.setQuantity(it.getQuantity());
                    items.add(si);
                }
                shipment.setItems(items);
            }

            if (!shipment.isValid()) throw new IllegalArgumentException("Invalid shipment payload");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Shipment.ENTITY_NAME,
                    String.valueOf(Shipment.ENTITY_VERSION),
                    shipment
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to create shipment", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating shipment", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Shipment by technicalId", description = "Retrieve the persisted Shipment by internal technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getShipment(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Shipment.ENTITY_NAME,
                    String.valueOf(Shipment.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request to get shipment", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving shipment", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "ShipmentRequest", description = "Shipment request payload")
    public static class ShipmentRequest {
        private String shipmentId;
        private String orderId;
        private List<ShipmentItemDto> items;
        private String trackingNumber;
        private String carrier;
        private String status;
        private String createdAt;

        @Data
        @Schema(name = "ShipmentItem")
        public static class ShipmentItemDto {
            private String sku;
            private Integer quantity;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the generated technical id")
    public static class TechnicalIdResponse {
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
