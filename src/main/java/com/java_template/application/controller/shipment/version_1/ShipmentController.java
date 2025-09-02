package com.java_template.application.controller.shipment.version_1;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ShipmentController handles shipment tracking and management operations.
 */
@RestController
@RequestMapping("/ui/shipments")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Mark shipment as ready for dispatch
     */
    @PostMapping("/{shipmentId}/ready")
    public ResponseEntity<Shipment> markReady(
            @PathVariable String shipmentId,
            @RequestParam String transition) {

        logger.info("Marking shipment ready: shipmentId={}, transition={}", shipmentId, transition);

        if (!"READY_FOR_DISPATCH".equals(transition)) {
            logger.warn("Invalid transition for shipment ready: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing shipment
            EntityResponse<Shipment> shipmentResponse = entityService.findByBusinessId(Shipment.class, shipmentId);
            Shipment shipment = shipmentResponse.getData();
            
            if (shipment == null) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

            // Update shipment with READY_FOR_DISPATCH transition
            EntityResponse<Shipment> updatedResponse = entityService.update(
                shipmentResponse.getId(),
                shipment,
                "READY_FOR_DISPATCH"
            );
            Shipment updatedShipment = updatedResponse.getData();

            logger.info("Shipment marked ready: shipmentId={}", updatedShipment.getShipmentId());
            return ResponseEntity.ok(updatedShipment);

        } catch (Exception e) {
            logger.error("Failed to mark shipment ready {}: {}", shipmentId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Dispatch shipment
     */
    @PostMapping("/{shipmentId}/dispatch")
    public ResponseEntity<Shipment> dispatch(
            @PathVariable String shipmentId,
            @RequestParam String transition) {

        logger.info("Dispatching shipment: shipmentId={}, transition={}", shipmentId, transition);

        if (!"DISPATCH_SHIPMENT".equals(transition)) {
            logger.warn("Invalid transition for shipment dispatch: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing shipment
            EntityResponse<Shipment> shipmentResponse = entityService.findByBusinessId(Shipment.class, shipmentId);
            Shipment shipment = shipmentResponse.getData();
            
            if (shipment == null) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

            // Update shipment with DISPATCH_SHIPMENT transition
            EntityResponse<Shipment> updatedResponse = entityService.update(
                shipmentResponse.getId(),
                shipment,
                "DISPATCH_SHIPMENT"
            );
            Shipment updatedShipment = updatedResponse.getData();

            logger.info("Shipment dispatched: shipmentId={}", updatedShipment.getShipmentId());
            return ResponseEntity.ok(updatedShipment);

        } catch (Exception e) {
            logger.error("Failed to dispatch shipment {}: {}", shipmentId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Confirm delivery of shipment
     */
    @PostMapping("/{shipmentId}/confirm-delivery")
    public ResponseEntity<Shipment> confirmDelivery(
            @PathVariable String shipmentId,
            @RequestParam String transition) {

        logger.info("Confirming delivery for shipment: shipmentId={}, transition={}", shipmentId, transition);

        if (!"CONFIRM_DELIVERY".equals(transition)) {
            logger.warn("Invalid transition for delivery confirmation: {}", transition);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Get existing shipment
            EntityResponse<Shipment> shipmentResponse = entityService.findByBusinessId(Shipment.class, shipmentId);
            Shipment shipment = shipmentResponse.getData();
            
            if (shipment == null) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

            // Update shipment with CONFIRM_DELIVERY transition
            EntityResponse<Shipment> updatedResponse = entityService.update(
                shipmentResponse.getId(),
                shipment,
                "CONFIRM_DELIVERY"
            );
            Shipment updatedShipment = updatedResponse.getData();

            logger.info("Delivery confirmed for shipment: shipmentId={}", updatedShipment.getShipmentId());
            return ResponseEntity.ok(updatedShipment);

        } catch (Exception e) {
            logger.error("Failed to confirm delivery for shipment {}: {}", shipmentId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get shipment details
     */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<Shipment> getShipment(@PathVariable String shipmentId) {
        logger.info("Getting shipment details: shipmentId={}", shipmentId);

        try {
            EntityResponse<Shipment> response = entityService.findByBusinessId(Shipment.class, shipmentId);
            Shipment shipment = response.getData();
            
            if (shipment == null) {
                logger.warn("Shipment not found: {}", shipmentId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found shipment: shipmentId={}, orderId={}, lineCount={}", 
                shipment.getShipmentId(), shipment.getOrderId(), 
                shipment.getLines() != null ? shipment.getLines().size() : 0);
            return ResponseEntity.ok(shipment);

        } catch (Exception e) {
            logger.error("Failed to get shipment {}: {}", shipmentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get shipment by order ID
     */
    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(@PathVariable String orderId) {
        logger.info("Getting shipment by order ID: orderId={}", orderId);

        try {
            SearchConditionRequest condition = new SearchConditionRequest();
            List<Condition> conditions = new ArrayList<>();
            conditions.add(Condition.of("orderId", "equals", orderId));
            condition.setConditions(conditions);

            Optional<EntityResponse<Shipment>> shipmentResponseOpt = entityService.getFirstItemByCondition(
                Shipment.class, condition, false);
            
            if (shipmentResponseOpt.isEmpty()) {
                logger.warn("Shipment not found for order: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentResponseOpt.get().getData();
            logger.info("Found shipment for order: shipmentId={}, orderId={}", 
                shipment.getShipmentId(), shipment.getOrderId());
            return ResponseEntity.ok(shipment);

        } catch (Exception e) {
            logger.error("Failed to get shipment by order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List all shipments
     */
    @GetMapping
    public ResponseEntity<List<EntityResponse<Shipment>>> listShipments(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        logger.info("Listing shipments: limit={}, offset={}", limit, offset);

        try {
            List<EntityResponse<Shipment>> shipments = entityService.getItems(Shipment.class, limit, offset, null);

            logger.info("Found {} shipments", shipments.size());
            return ResponseEntity.ok(shipments);

        } catch (Exception e) {
            logger.error("Failed to list shipments: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
