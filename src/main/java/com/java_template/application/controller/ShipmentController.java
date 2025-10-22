package com.java_template.application.controller;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * ABOUTME: REST controller for Shipment endpoints including status retrieval and state transitions.
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
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            logger.error("Failed to get shipment {}: {}", shipmentId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve shipment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Mark shipment as ready to send
     * POST /ui/shipment/{shipmentId}/ready-to-send
     */
    @PostMapping("/{shipmentId}/ready-to-send")
    public ResponseEntity<EntityWithMetadata<Shipment>> readyToSend(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            shipment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "READY_TO_SEND");
            logger.info("Shipment {} marked as ready to send", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to mark shipment {} as ready to send: {}", shipmentId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to mark shipment as ready to send: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Mark shipment as sent
     * POST /ui/shipment/{shipmentId}/mark-sent
     */
    @PostMapping("/{shipmentId}/mark-sent")
    public ResponseEntity<EntityWithMetadata<Shipment>> markSent(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            shipment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "MARK_SENT");
            logger.info("Shipment {} marked as sent", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to mark shipment {} as sent: {}", shipmentId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to mark shipment as sent: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Mark shipment as delivered
     * POST /ui/shipment/{shipmentId}/mark-delivered
     */
    @PostMapping("/{shipmentId}/mark-delivered")
    public ResponseEntity<EntityWithMetadata<Shipment>> markDelivered(@PathVariable String shipmentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.findByBusinessId(
                    modelSpec, shipmentId, "shipmentId", Shipment.class);

            if (shipmentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Shipment shipment = shipmentWithMetadata.entity();
            shipment.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Shipment> response = entityService.update(
                    shipmentWithMetadata.metadata().getId(), shipment, "MARK_DELIVERED");
            logger.info("Shipment {} marked as delivered", shipmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to mark shipment {} as delivered: {}", shipmentId, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to mark shipment as delivered: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

