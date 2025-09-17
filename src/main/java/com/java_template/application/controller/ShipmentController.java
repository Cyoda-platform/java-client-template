package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ShipmentController - REST endpoints for shipment management
 * 
 * Handles shipment tracking and status updates for order fulfillment.
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
            EntityWithMetadata<Shipment> shipmentResponse = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(shipmentResponse);
        } catch (Exception e) {
            logger.error("Error getting shipment: {}", shipmentId, e);
            return ResponseEntity.badRequest().build();
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

            // Search for shipments by order ID
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Shipment>> shipments = entityService.search(modelSpec, groupCondition, Shipment.class);
            return ResponseEntity.ok(shipments);
        } catch (Exception e) {
            logger.error("Error getting shipments for order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update shipment status (manual transitions)
     * POST /ui/shipment/{shipmentId}/transition
     */
    @PostMapping("/{shipmentId}/transition")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipmentStatus(
            @PathVariable String shipmentId,
            @RequestBody TransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentResponse = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentResponse.entity();
            shipment.setUpdatedAt(LocalDateTime.now());

            // Update shipment with specified transition
            EntityWithMetadata<Shipment> updatedResponse = entityService.update(
                    shipmentResponse.metadata().getId(), shipment, request.getTransition());

            logger.info("Shipment {} transitioned with: {}", shipmentId, request.getTransition());
            return ResponseEntity.ok(updatedResponse);
        } catch (Exception e) {
            logger.error("Error updating shipment status: {}", shipmentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update picking quantities
     * POST /ui/shipment/{shipmentId}/pick
     */
    @PostMapping("/{shipmentId}/pick")
    public ResponseEntity<EntityWithMetadata<Shipment>> updatePickingQuantities(
            @PathVariable String shipmentId,
            @RequestBody List<PickingUpdate> pickingUpdates) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentResponse = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentResponse.entity();

            // Update picking quantities
            for (PickingUpdate update : pickingUpdates) {
                Shipment.ShipmentLine line = shipment.getLines().stream()
                        .filter(l -> update.getSku().equals(l.getSku()))
                        .findFirst()
                        .orElse(null);

                if (line != null) {
                    line.setQtyPicked(update.getQtyPicked());
                    logger.debug("Updated picking for {}: picked {}", update.getSku(), update.getQtyPicked());
                }
            }

            shipment.setUpdatedAt(LocalDateTime.now());

            // Update shipment without transition (stays in same state)
            EntityWithMetadata<Shipment> updatedResponse = entityService.update(
                    shipmentResponse.metadata().getId(), shipment, null);

            logger.info("Picking quantities updated for shipment: {}", shipmentId);
            return ResponseEntity.ok(updatedResponse);
        } catch (Exception e) {
            logger.error("Error updating picking quantities: {}", shipmentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update shipping quantities
     * POST /ui/shipment/{shipmentId}/ship
     */
    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShippingQuantities(
            @PathVariable String shipmentId,
            @RequestBody List<ShippingUpdate> shippingUpdates) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentResponse = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentResponse == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentResponse.entity();

            // Update shipping quantities
            for (ShippingUpdate update : shippingUpdates) {
                Shipment.ShipmentLine line = shipment.getLines().stream()
                        .filter(l -> update.getSku().equals(l.getSku()))
                        .findFirst()
                        .orElse(null);

                if (line != null) {
                    line.setQtyShipped(update.getQtyShipped());
                    logger.debug("Updated shipping for {}: shipped {}", update.getSku(), update.getQtyShipped());
                }
            }

            shipment.setUpdatedAt(LocalDateTime.now());

            // Update shipment without transition (stays in same state)
            EntityWithMetadata<Shipment> updatedResponse = entityService.update(
                    shipmentResponse.metadata().getId(), shipment, null);

            logger.info("Shipping quantities updated for shipment: {}", shipmentId);
            return ResponseEntity.ok(updatedResponse);
        } catch (Exception e) {
            logger.error("Error updating shipping quantities: {}", shipmentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request DTOs
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transition;
    }

    @Getter
    @Setter
    public static class PickingUpdate {
        private String sku;
        private Integer qtyPicked;
    }

    @Getter
    @Setter
    public static class ShippingUpdate {
        private String sku;
        private Integer qtyShipped;
    }
}
