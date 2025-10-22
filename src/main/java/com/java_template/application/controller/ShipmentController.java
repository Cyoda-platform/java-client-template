package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: Shipment REST controller exposing shipment endpoints for retrieving
 * and updating shipment status.
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
     * Get shipment by ID
     * GET /ui/shipment/{shipmentId}
     */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<EntityWithMetadata<Shipment>> getShipment(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> response = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve shipment with ID '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get shipment by order ID
     * GET /ui/shipment/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<EntityWithMetadata<Shipment>> getShipmentByOrderId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Shipment.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve shipment for order '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update shipment
     * PUT /ui/shipment/{shipmentId}
     */
    @PutMapping("/{shipmentId}")
    public ResponseEntity<EntityWithMetadata<Shipment>> updateShipment(
            @PathVariable String shipmentId,
            @Valid @RequestBody Shipment shipment,
            @RequestParam(required = false) String transition) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> existing = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (existing == null) {
                return ResponseEntity.notFound().build();
            }

            shipment.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Shipment> response = entityService.update(
                    existing.metadata().getId(), shipment, transition);
            logger.info("Shipment updated with ID: {}", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update shipment with ID '%s': %s", shipmentId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

