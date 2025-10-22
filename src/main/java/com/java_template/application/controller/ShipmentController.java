package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: REST controller for Shipment operations including shipment tracking,
 * status updates, and fulfillment workflow management.
 */
@RestController
@RequestMapping("/ui/shipment")
@CrossOrigin(origins = "*")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ShipmentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
    public ResponseEntity<List<EntityWithMetadata<Shipment>>> getShipmentsByOrderId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            
            SimpleCondition orderCondition = new SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
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
     * PUT /ui/shipment/{shipmentId}/status?transition=complete_picking
     */
    @PutMapping("/{shipmentId}/status")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipmentStatus(
            @PathVariable String shipmentId,
            @RequestParam String transition) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            
            // Update status based on transition
            String newStatus = getStatusFromTransition(transition);
            if (newStatus != null) {
                shipment.setStatus(newStatus);
            }
            shipment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, transition);
            
            logger.info("Shipment status updated: {} - transition: {} -> status: {}", 
                       shipmentId, transition, shipment.getStatus());
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
     * List all shipments with optional status filter
     * GET /ui/shipment?status=PICKING
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Shipment>>> listShipments(
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            
            if (status == null || status.trim().isEmpty()) {
                // No filter - return all shipments
                List<EntityWithMetadata<Shipment>> shipments = entityService.findAll(modelSpec, Shipment.class);
                return ResponseEntity.ok(shipments);
            } else {
                // Filter by status (status is in metadata, not entity)
                List<EntityWithMetadata<Shipment>> allShipments = entityService.findAll(modelSpec, Shipment.class);
                List<EntityWithMetadata<Shipment>> filteredShipments = allShipments.stream()
                        .filter(shipment -> status.equals(shipment.metadata().getState()))
                        .toList();
                return ResponseEntity.ok(filteredShipments);
            }
        } catch (Exception e) {
            logger.error("Failed to list shipments", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list shipments: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Map transition names to status values
     */
    private String getStatusFromTransition(String transition) {
        return switch (transition) {
            case "complete_picking" -> "WAITING_TO_SEND";
            case "mark_sent" -> "SENT";
            case "mark_delivered" -> "DELIVERED";
            default -> null;
        };
    }
}
