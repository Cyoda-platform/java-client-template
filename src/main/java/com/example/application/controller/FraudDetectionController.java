package com.example.application.controller;

import com.example.application.entity.fraud_detection.version_1.FraudDetection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FraudDetectionController - REST API for fraud detection and review
 * Handles fraud analysis, manual review, and decision logging
 */
@RestController
@RequestMapping("/ui/fraud-detection")
@CrossOrigin(origins = "*")
public class FraudDetectionController {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FraudDetectionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<FraudDetection>> createFraudDetection(
            @RequestBody FraudDetection fraudDetection) {
        try {
            // Check for duplicate fraud detection ID
            ModelSpec modelSpec = new ModelSpec()
                    .withName(FraudDetection.ENTITY_NAME)
                    .withVersion(FraudDetection.ENTITY_VERSION);
            
            EntityWithMetadata<FraudDetection> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, fraudDetection.getFraudDetectionId(), "fraudDetectionId", FraudDetection.class);

            if (existing != null) {
                logger.warn("Fraud detection with ID {} already exists", fraudDetection.getFraudDetectionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Fraud detection already exists with ID: " + fraudDetection.getFraudDetectionId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<FraudDetection> response = entityService.create(fraudDetection);
            logger.info("Fraud detection created: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create fraud detection", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to create fraud detection: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<FraudDetection>> getFraudDetection(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(FraudDetection.ENTITY_NAME)
                    .withVersion(FraudDetection.ENTITY_VERSION);
            
            EntityWithMetadata<FraudDetection> response = entityService.getById(
                    id, modelSpec, FraudDetection.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve fraud detection", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve fraud detection: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<FraudDetection>> approveFraudDetection(
            @PathVariable UUID id,
            @RequestParam String reviewedBy) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(FraudDetection.ENTITY_NAME)
                    .withVersion(FraudDetection.ENTITY_VERSION);
            
            EntityWithMetadata<FraudDetection> current = entityService.getById(
                    id, modelSpec, FraudDetection.class);
            
            current.entity().setReviewStatus("APPROVED");
            current.entity().setReviewedBy(reviewedBy);
            current.entity().setReviewedAt(LocalDateTime.now());
            EntityWithMetadata<FraudDetection> response = entityService.update(id, current.entity(), "APPROVE_REVIEW");
            logger.info("Fraud detection approved: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to approve fraud detection", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to approve fraud detection: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<EntityWithMetadata<FraudDetection>> rejectFraudDetection(
            @PathVariable UUID id,
            @RequestParam String reviewedBy) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(FraudDetection.ENTITY_NAME)
                    .withVersion(FraudDetection.ENTITY_VERSION);
            
            EntityWithMetadata<FraudDetection> current = entityService.getById(
                    id, modelSpec, FraudDetection.class);
            
            current.entity().setReviewStatus("REJECTED");
            current.entity().setReviewedBy(reviewedBy);
            current.entity().setReviewedAt(LocalDateTime.now());
            EntityWithMetadata<FraudDetection> response = entityService.update(id, current.entity(), "REJECT_REVIEW");
            logger.info("Fraud detection rejected: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to reject fraud detection", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to reject fraud detection: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFraudDetection(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Fraud detection deleted: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete fraud detection", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to delete fraud detection: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

