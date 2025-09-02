package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/ui/shipment")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);

    @Autowired
    private EntityService entityService;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(@PathVariable String orderId) {
        logger.info("Getting shipment for order: {}", orderId);

        try {
            Shipment shipment = getShipmentByOrderId(orderId);
            if (shipment != null) {
                return ResponseEntity.ok(shipment);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting shipment for order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{shipmentId}/ready-to-send")
    public ResponseEntity<Shipment> markReadyToSend(
            @PathVariable String shipmentId,
            @RequestParam(required = false) String transitionName) {

        logger.info("Marking shipment ready to send: {}", shipmentId);

        try {
            // Get shipment
            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            // Update shipment with transition
            UUID shipmentTechnicalId = getShipmentTechnicalId(shipmentId);
            String transition = transitionName != null ? transitionName : "READY_TO_SEND";
            EntityResponse<Shipment> updatedShipmentResponse = entityService.update(shipmentTechnicalId, shipment, transition);

            return ResponseEntity.ok(updatedShipmentResponse.getData());

        } catch (Exception e) {
            logger.error("Error marking shipment ready to send: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{shipmentId}/mark-sent")
    public ResponseEntity<Shipment> markSent(
            @PathVariable String shipmentId,
            @RequestParam(required = false) String transitionName) {

        logger.info("Marking shipment as sent: {}", shipmentId);

        try {
            // Get shipment
            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            // Update quantities to reflect shipment
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                line.setQtyShipped(line.getQtyOrdered());
            }

            // Update shipment with transition
            UUID shipmentTechnicalId = getShipmentTechnicalId(shipmentId);
            String transition = transitionName != null ? transitionName : "MARK_SENT";
            EntityResponse<Shipment> updatedShipmentResponse = entityService.update(shipmentTechnicalId, shipment, transition);

            return ResponseEntity.ok(updatedShipmentResponse.getData());

        } catch (Exception e) {
            logger.error("Error marking shipment as sent: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/{shipmentId}/mark-delivered")
    public ResponseEntity<Shipment> markDelivered(
            @PathVariable String shipmentId,
            @RequestParam(required = false) String transitionName) {

        logger.info("Marking shipment as delivered: {}", shipmentId);

        try {
            // Get shipment
            Shipment shipment = getShipmentByShipmentId(shipmentId);
            if (shipment == null) {
                return ResponseEntity.notFound().build();
            }

            // Update shipment with transition
            UUID shipmentTechnicalId = getShipmentTechnicalId(shipmentId);
            String transition = transitionName != null ? transitionName : "MARK_DELIVERED";
            EntityResponse<Shipment> updatedShipmentResponse = entityService.update(shipmentTechnicalId, shipment, transition);

            return ResponseEntity.ok(updatedShipmentResponse.getData());

        } catch (Exception e) {
            logger.error("Error marking shipment as delivered: {}", shipmentId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Shipment getShipmentByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);

            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving shipment by orderId: {}", orderId, e);
            return null;
        }
    }

    private Shipment getShipmentByShipmentId(String shipmentId) {
        try {
            Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipmentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(shipmentIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);

            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving shipment by shipmentId: {}", shipmentId, e);
            return null;
        }
    }

    private UUID getShipmentTechnicalId(String shipmentId) {
        try {
            Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipmentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(shipmentIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);

            return shipmentResponse.map(response -> response.getMetadata().getId()).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving shipment technical ID: {}", shipmentId, e);
            return null;
        }
    }
}
