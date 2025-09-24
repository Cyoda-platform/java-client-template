package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Shipment Controller for OMS fulfillment tracking
 * Provides REST endpoints for shipment status tracking
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
        logger.info("Getting shipment: {}", shipmentId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Shipment.ENTITY_NAME);
            modelSpec.setVersion(Shipment.ENTITY_VERSION);

            EntityWithMetadata<Shipment> shipment = entityService.findByBusinessId(modelSpec, shipmentId, "shipmentId", Shipment.class);
            
            if (shipment != null) {
                logger.info("Found shipment: {} with status: {}", shipmentId, shipment.entity().getStatus());
                return ResponseEntity.ok(shipment);
            } else {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting shipment: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update shipment status
     * PUT /ui/shipment/{shipmentId}/status/{status}
     */
    @PutMapping("/{shipmentId}/status/{status}")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipmentStatus(
            @PathVariable String shipmentId, 
            @PathVariable String status) {
        
        logger.info("Updating shipment {} to status: {}", shipmentId, status);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Shipment.ENTITY_NAME);
            modelSpec.setVersion(Shipment.ENTITY_VERSION);

            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                modelSpec, shipmentId, "shipmentId", Shipment.class);
            
            if (shipmentWithMetadata == null) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            // Determine transition based on status
            String transition = getTransitionForStatus(status);
            if (transition == null) {
                logger.warn("Invalid status transition: {}", status);
                return ResponseEntity.badRequest().build();
            }

            // Update shipment status
            shipment.setStatus(status.toUpperCase());
            
            EntityWithMetadata<Shipment> updatedShipment = entityService.updateByBusinessId(
                shipment, "shipmentId", transition);

            logger.info("Updated shipment {} to status: {}", shipmentId, status);
            return ResponseEntity.ok(updatedShipment);

        } catch (Exception e) {
            logger.error("Error updating shipment status: {} to {}", shipmentId, status, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get transition name for status update
     */
    private String getTransitionForStatus(String status) {
        switch (status.toLowerCase()) {
            case "waiting_to_send":
                return "ready_to_send";
            case "sent":
                return "mark_sent";
            case "delivered":
                return "mark_delivered";
            default:
                return null;
        }
    }
}
