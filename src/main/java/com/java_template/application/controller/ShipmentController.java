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

import java.util.List;

/**
 * ABOUTME: REST controller for Shipment operations including shipment tracking,
 * picking progress updates, and shipment lifecycle management.
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
     * Get shipment by shipmentId
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
     * Get shipment by orderId
     * GET /ui/shipment/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<EntityWithMetadata<Shipment>> getShipmentByOrderId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipment = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Shipment.class);

            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(shipment);
        } catch (Exception e) {
            logger.error("Failed to retrieve shipment for order ID: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve shipment for order ID '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update picking progress
     * PATCH /ui/shipment/{shipmentId}/picking
     */
    @PatchMapping("/{shipmentId}/picking")
    public ResponseEntity<EntityWithMetadata<Shipment>> updatePickingProgress(
            @PathVariable String shipmentId,
            @Valid @RequestBody UpdatePickingRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            if (!"PICKING".equals(shipment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Shipment must be PICKING to update progress, current status: %s", shipment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Update picking quantities
            updatePickingQuantities(shipment, request.getPickingUpdates());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "update_picking_progress");
            logger.info("Picking progress updated for shipment: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update picking progress for shipment: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update picking progress for shipment '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Complete picking
     * POST /ui/shipment/{shipmentId}/complete-picking
     */
    @PostMapping("/{shipmentId}/complete-picking")
    public ResponseEntity<EntityWithMetadata<Shipment>> completePicking(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            if (!"PICKING".equals(shipment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Shipment must be PICKING to complete picking, current status: %s", shipment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "complete_picking");
            logger.info("Picking completed for shipment: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to complete picking for shipment: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to complete picking for shipment '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Ship package
     * POST /ui/shipment/{shipmentId}/ship
     */
    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<EntityWithMetadata<Shipment>> shipPackage(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            if (!"WAITING_TO_SEND".equals(shipment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Shipment must be WAITING_TO_SEND to ship, current status: %s", shipment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "ship_package");
            logger.info("Package shipped for shipment: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to ship package for shipment: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to ship package for shipment '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Confirm delivery
     * POST /ui/shipment/{shipmentId}/confirm-delivery
     */
    @PostMapping("/{shipmentId}/confirm-delivery")
    public ResponseEntity<EntityWithMetadata<Shipment>> confirmDelivery(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            if (!"SENT".equals(shipment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Shipment must be SENT to confirm delivery, current status: %s", shipment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "confirm_delivery");
            logger.info("Delivery confirmed for shipment: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to confirm delivery for shipment: {}", shipmentId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to confirm delivery for shipment '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update picking quantities for shipment lines
     */
    private void updatePickingQuantities(Shipment shipment, List<PickingUpdate> pickingUpdates) {
        if (shipment.getLines() == null || pickingUpdates == null) {
            return;
        }

        for (PickingUpdate update : pickingUpdates) {
            Shipment.ShipmentLine line = shipment.getLines().stream()
                    .filter(l -> update.getSku().equals(l.getSku()))
                    .findFirst()
                    .orElse(null);

            if (line != null) {
                line.setQtyPicked(update.getQtyPicked());
                logger.debug("Updated picking for SKU {}: picked {}/{}", 
                           update.getSku(), update.getQtyPicked(), line.getQtyOrdered());
            }
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class UpdatePickingRequest {
        private List<PickingUpdate> pickingUpdates;
    }

    @Getter
    @Setter
    public static class PickingUpdate {
        private String sku;
        private Integer qtyPicked;
    }
}
