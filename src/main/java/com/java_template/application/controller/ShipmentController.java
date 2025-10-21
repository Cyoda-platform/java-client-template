package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Shipment controller providing REST endpoints for shipment management
 * including shipment tracking and status updates.
 */
@RestController
@RequestMapping("/ui/shipment")
@CrossOrigin(origins = "*")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Get shipment by shipment ID
     * GET /ui/shipment/{shipmentId}
     */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<EntityWithMetadata<Shipment>> getShipment(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipment = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(shipment);
        } catch (Exception e) {
            logger.error("Failed to retrieve shipment with ID: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve shipment with ID '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get shipments by order ID
     * GET /ui/shipment/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<EntityWithMetadata<Shipment>>> getShipmentsByOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            
            // Search for shipments with matching order ID
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            org.cyoda.cloud.api.event.common.condition.SimpleCondition orderCondition = 
                new org.cyoda.cloud.api.event.common.condition.SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(org.cyoda.cloud.api.event.common.condition.Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            org.cyoda.cloud.api.event.common.condition.GroupCondition condition = 
                new org.cyoda.cloud.api.event.common.condition.GroupCondition()
                    .withOperator(org.cyoda.cloud.api.event.common.condition.GroupCondition.Operator.AND)
                    .withConditions(List.of(orderCondition));

            List<EntityWithMetadata<Shipment>> shipments = entityService.search(modelSpec, condition, Shipment.class);
            return ResponseEntity.ok(shipments);
        } catch (Exception e) {
            logger.error("Failed to retrieve shipments for order: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve shipments for order '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update shipment status
     * PUT /ui/shipment/{shipmentId}/status
     */
    @PutMapping("/{shipmentId}/status")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipmentStatus(
            @PathVariable String shipmentId,
            @Valid @RequestBody UpdateShipmentStatusRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            shipment.setStatus(request.getStatus());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Determine transition based on status
            String transition = getTransitionForStatus(request.getStatus());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, transition);
            logger.info("Shipment status updated: {} -> {}", shipmentId, request.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update shipment status: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update shipment status '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update shipment quantities
     * PUT /ui/shipment/{shipmentId}/quantities
     */
    @PutMapping("/{shipmentId}/quantities")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipmentQuantities(
            @PathVariable String shipmentId,
            @Valid @RequestBody UpdateShipmentQuantitiesRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            // Update quantities for each line
            for (UpdateShipmentQuantitiesRequest.LineQuantityUpdate update : request.getLines()) {
                Shipment.ShipmentLine line = shipment.getLines().stream()
                        .filter(l -> update.getSku().equals(l.getSku()))
                        .findFirst()
                        .orElse(null);
                
                if (line != null) {
                    if (update.getQtyPicked() != null) {
                        line.setQtyPicked(update.getQtyPicked());
                    }
                    if (update.getQtyShipped() != null) {
                        line.setQtyShipped(update.getQtyShipped());
                    }
                }
            }
            
            shipment.setUpdatedAt(LocalDateTime.now());

            // Update without transition (quantities only)
            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, null);
            logger.info("Shipment quantities updated: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update shipment quantities: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update shipment quantities '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get workflow transition for shipment status
     */
    private String getTransitionForStatus(String status) {
        return switch (status) {
            case "WAITING_TO_SEND" -> "complete_picking";
            case "SENT" -> "mark_sent";
            case "DELIVERED" -> "mark_delivered";
            default -> null;
        };
    }

    /**
     * Request DTO for updating shipment status
     */
    @Getter
    @Setter
    public static class UpdateShipmentStatusRequest {
        private String status;
    }

    /**
     * Request DTO for updating shipment quantities
     */
    @Getter
    @Setter
    public static class UpdateShipmentQuantitiesRequest {
        private List<LineQuantityUpdate> lines;

        @Getter
        @Setter
        public static class LineQuantityUpdate {
            private String sku;
            private Integer qtyPicked;
            private Integer qtyShipped;
        }
    }
}
