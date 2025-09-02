package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Shipment>> createShipment(@RequestBody Shipment shipment) {
        try {
            EntityResponse<Shipment> response = entityService.save(shipment);
            logger.info("Shipment created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating shipment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Shipment>> getShipment(@PathVariable UUID id) {
        try {
            EntityResponse<Shipment> response = entityService.getItem(id, Shipment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving shipment with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/shipmentId/{shipmentId}")
    public ResponseEntity<EntityResponse<Shipment>> getShipmentByShipmentId(@PathVariable String shipmentId) {
        try {
            Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipmentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(shipmentIdCondition));

            Optional<EntityResponse<Shipment>> response = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving shipment with shipmentId: {}", shipmentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<EntityResponse<Shipment>> getShipmentByOrderId(@PathVariable String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Shipment>> response = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving shipment with orderId: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Shipment>>> getAllShipments() {
        try {
            List<EntityResponse<Shipment>> shipments = entityService.findAll(Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION);
            return ResponseEntity.ok(shipments);
        } catch (Exception e) {
            logger.error("Error retrieving all shipments", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Shipment>> updateShipment(
            @PathVariable UUID id, 
            @RequestBody Shipment shipment,
            @RequestParam(required = false) String transition) {
        try {
            EntityResponse<Shipment> response = entityService.update(id, shipment, transition);
            logger.info("Shipment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating shipment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Shipment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting shipment with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/create")
    public ResponseEntity<EntityResponse<Shipment>> createShipmentFromOrder(@PathVariable UUID id) {
        try {
            EntityResponse<Shipment> shipmentResponse = entityService.getItem(id, Shipment.class);
            Shipment shipment = shipmentResponse.getData();
            EntityResponse<Shipment> response = entityService.update(id, shipment, "CREATE_SHIPMENT");
            logger.info("Shipment created from order with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating shipment from order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/ready-to-send")
    public ResponseEntity<EntityResponse<Shipment>> markShipmentReadyToSend(@PathVariable UUID id) {
        try {
            EntityResponse<Shipment> shipmentResponse = entityService.getItem(id, Shipment.class);
            Shipment shipment = shipmentResponse.getData();
            EntityResponse<Shipment> response = entityService.update(id, shipment, "READY_TO_SEND");
            logger.info("Shipment marked ready to send with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking shipment ready to send with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/mark-sent")
    public ResponseEntity<EntityResponse<Shipment>> markShipmentSent(@PathVariable UUID id) {
        try {
            EntityResponse<Shipment> shipmentResponse = entityService.getItem(id, Shipment.class);
            Shipment shipment = shipmentResponse.getData();
            EntityResponse<Shipment> response = entityService.update(id, shipment, "MARK_SENT");
            logger.info("Shipment marked as sent with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking shipment as sent with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/mark-delivered")
    public ResponseEntity<EntityResponse<Shipment>> markShipmentDelivered(@PathVariable UUID id) {
        try {
            EntityResponse<Shipment> shipmentResponse = entityService.getItem(id, Shipment.class);
            Shipment shipment = shipmentResponse.getData();
            EntityResponse<Shipment> response = entityService.update(id, shipment, "MARK_DELIVERED");
            logger.info("Shipment marked as delivered with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking shipment as delivered with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
