package com.example.application.controller;

import com.example.application.entity.subscription.version_1.Subscription;
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
import java.util.UUID;

/**
 * SubscriptionController - REST API for subscription management
 * Handles subscription creation, renewal, upgrades, downgrades, and cancellation
 */
@RestController
@RequestMapping("/ui/subscription")
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriptionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscription>> createSubscription(
            @RequestBody Subscription subscription) {
        try {
            // Check for duplicate subscription ID
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscription.ENTITY_NAME)
                    .withVersion(Subscription.ENTITY_VERSION);
            
            EntityWithMetadata<Subscription> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, subscription.getSubscriptionId(), "subscriptionId", Subscription.class);

            if (existing != null) {
                logger.warn("Subscription with ID {} already exists", subscription.getSubscriptionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Subscription already exists with ID: " + subscription.getSubscriptionId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Subscription> response = entityService.create(subscription);
            logger.info("Subscription created: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create subscription", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to create subscription: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscription>> getSubscription(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscription.ENTITY_NAME)
                    .withVersion(Subscription.ENTITY_VERSION);
            
            EntityWithMetadata<Subscription> response = entityService.getById(
                    id, modelSpec, Subscription.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscription", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve subscription: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscription>> updateSubscription(
            @PathVariable UUID id,
            @RequestBody Subscription subscription,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Subscription> response = entityService.update(id, subscription, transition);
            logger.info("Subscription updated: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update subscription", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to update subscription: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<Subscription>> cancelSubscription(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscription.ENTITY_NAME)
                    .withVersion(Subscription.ENTITY_VERSION);
            
            EntityWithMetadata<Subscription> current = entityService.getById(
                    id, modelSpec, Subscription.class);
            
            current.entity().setStatus("CANCELED");
            current.entity().setCancelReason(reason);
            EntityWithMetadata<Subscription> response = entityService.update(id, current.entity(), "CANCEL");
            logger.info("Subscription canceled: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to cancel subscription", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to cancel subscription: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Subscription deleted: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete subscription", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to delete subscription: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

