package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ui/shipment")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);
    private final EntityService entityService;

    public ShipmentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Map<String, Object>> getShipmentByOrderId(@PathVariable String orderId) {
        try {
            logger.info("Getting shipment for order: {}", orderId);

            Shipment shipment = findShipmentByOrderId(orderId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            // Get shipment state
            String state = getShipmentState(shipment.getShipmentId());

            Map<String, Object> response = convertToShipmentResponse(shipment, state);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting shipment for order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{shipmentId}/ready-to-send")
    public ResponseEntity<Map<String, Object>> readyToSend(@PathVariable String shipmentId) {
        try {
            logger.info("Marking shipment ready to send: {}", shipmentId);

            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            UUID shipmentEntityId = getShipmentEntityId(shipmentId);
            if (shipmentEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Shipment> updatedShipment = entityService.update(shipmentEntityId, shipment, "ready_to_send");

            Map<String, Object> response = new HashMap<>();
            response.put("shipmentId", shipmentId);
            response.put("status", updatedShipment.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking shipment ready to send: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{shipmentId}/mark-sent")
    public ResponseEntity<Map<String, Object>> markSent(@PathVariable String shipmentId) {
        try {
            logger.info("Marking shipment as sent: {}", shipmentId);

            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            UUID shipmentEntityId = getShipmentEntityId(shipmentId);
            if (shipmentEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Shipment> updatedShipment = entityService.update(shipmentEntityId, shipment, "mark_sent");

            Map<String, Object> response = new HashMap<>();
            response.put("shipmentId", shipmentId);
            response.put("status", updatedShipment.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking shipment as sent: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{shipmentId}/mark-delivered")
    public ResponseEntity<Map<String, Object>> markDelivered(@PathVariable String shipmentId) {
        try {
            logger.info("Marking shipment as delivered: {}", shipmentId);

            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            UUID shipmentEntityId = getShipmentEntityId(shipmentId);
            if (shipmentEntityId == null) {
                return ResponseEntity.notFound().build();
            }

            EntityResponse<Shipment> updatedShipment = entityService.update(shipmentEntityId, shipment, "mark_delivered");

            Map<String, Object> response = new HashMap<>();
            response.put("shipmentId", shipmentId);
            response.put("status", updatedShipment.getMetadata().getState());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error marking shipment as delivered: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Shipment findShipmentByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, 
                Shipment.ENTITY_NAME, 
                Shipment.ENTITY_VERSION, 
                condition, 
                true
            );

            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching shipment by order ID: {}", orderId, e);
            return null;
        }
    }

    private Shipment getShipmentByShipmentId(String shipmentId) {
        try {
            Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipmentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(List.of(shipmentIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, 
                Shipment.ENTITY_NAME, 
                Shipment.ENTITY_VERSION, 
                condition, 
                true
            );

            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching shipment by ID: {}", shipmentId, e);
            return null;
        }
    }

    private UUID getShipmentEntityId(String shipmentId) {
        // TODO: Implement proper entity ID lookup
        return null;
    }

    private String getShipmentState(String shipmentId) {
        // TODO: Implement proper state lookup
        return "picking";
    }

    private Map<String, Object> convertToShipmentResponse(Shipment shipment, String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("shipmentId", shipment.getShipmentId());
        response.put("orderId", shipment.getOrderId());
        response.put("status", status);
        response.put("lines", shipment.getLines());
        response.put("createdAt", shipment.getCreatedAt());
        response.put("updatedAt", shipment.getUpdatedAt());
        return response;
    }
}
