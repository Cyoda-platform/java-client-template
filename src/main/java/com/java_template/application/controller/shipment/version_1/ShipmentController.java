package com.java_template.application.controller.shipment.version_1;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ShipmentController handles REST API endpoints for shipment operations.
 * This controller is a proxy to the EntityService for Shipment entities.
 */
@RestController
@RequestMapping("/ui/shipment")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Get shipment details by order ID.
     * 
     * @param orderId Order identifier
     * @return Shipment entity
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(@PathVariable String orderId) {
        logger.info("Getting shipment by order ID: {}", orderId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("orderId", "equals", orderId));
            
            var shipmentResponse = entityService.getFirstItemByCondition(Shipment.class, condition, false);
            
            if (shipmentResponse.isPresent()) {
                return ResponseEntity.ok(shipmentResponse.get().getData());
            } else {
                logger.warn("Shipment not found for order: {}", orderId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting shipment by order ID: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update shipment (for status transitions).
     * 
     * @param shipmentId Shipment identifier
     * @param transition Workflow transition name (required)
     * @param shipment Shipment entity (optional updates)
     * @return Updated Shipment entity
     */
    @PutMapping("/{shipmentId}")
    public ResponseEntity<Shipment> updateShipment(
            @PathVariable String shipmentId,
            @RequestParam String transition,
            @RequestBody(required = false) Shipment shipment) {
        
        logger.info("Updating shipment: {} with transition: {}", shipmentId, transition);

        try {
            // Get existing shipment
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("shipmentId", "equals", shipmentId));
            
            var shipmentResponse = entityService.getFirstItemByCondition(Shipment.class, condition, false);
            if (shipmentResponse.isEmpty()) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }
            
            Shipment existingShipment = shipmentResponse.get().getData();
            UUID entityId = shipmentResponse.get().getMetadata().getId();
            
            // If shipment data is provided, merge updates
            if (shipment != null) {
                // Update shipment lines if provided
                if (shipment.getLines() != null) {
                    // Update quantities for existing lines
                    for (Shipment.ShipmentLine updatedLine : shipment.getLines()) {
                        existingShipment.getLines().stream()
                            .filter(line -> line.getSku().equals(updatedLine.getSku()))
                            .findFirst()
                            .ifPresent(existingLine -> {
                                if (updatedLine.getQtyPicked() != null) {
                                    existingLine.setQtyPicked(updatedLine.getQtyPicked());
                                }
                                if (updatedLine.getQtyShipped() != null) {
                                    existingLine.setQtyShipped(updatedLine.getQtyShipped());
                                }
                            });
                    }
                }
            }
            
            existingShipment.setUpdatedAt(LocalDateTime.now());
            
            // Update shipment with transition
            EntityResponse<Shipment> updatedShipment = entityService.update(entityId, existingShipment, transition);
            return ResponseEntity.ok(updatedShipment.getData());
            
        } catch (Exception e) {
            logger.error("Error updating shipment", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get shipment details by shipment ID.
     * 
     * @param shipmentId Shipment identifier
     * @return Shipment entity
     */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<Shipment> getShipment(@PathVariable String shipmentId) {
        logger.info("Getting shipment: {}", shipmentId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("shipmentId", "equals", shipmentId));
            
            var shipmentResponse = entityService.getFirstItemByCondition(Shipment.class, condition, false);
            
            if (shipmentResponse.isPresent()) {
                return ResponseEntity.ok(shipmentResponse.get().getData());
            } else {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting shipment: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
