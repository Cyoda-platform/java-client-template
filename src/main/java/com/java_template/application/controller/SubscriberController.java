package com.java_template.application.controller;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * SubscriberController
 * 
 * REST API for managing email subscribers and subscription preferences.
 */
@RestController
@RequestMapping("/api/subscribers")
@CrossOrigin(origins = "*")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new subscriber
     * POST /api/subscribers
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Generate unique subscriberId if not provided
            if (subscriber.getSubscriberId() == null || subscriber.getSubscriberId().trim().isEmpty()) {
                subscriber.setSubscriberId("sub-" + UUID.randomUUID().toString().substring(0, 8));
            }

            // Validate required fields
            if (!subscriber.isValid()) {
                logger.warn("Invalid Subscriber provided: {}", subscriber);
                return ResponseEntity.badRequest().build();
            }

            // Create the Subscriber entity (creates in initial_state, then auto-transitions to created)
            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with ID: {} and business ID: {}", 
                       response.metadata().getId(), subscriber.getSubscriberId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate subscription
     * PUT /api/subscribers/{id}/activate
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> activateSubscription(@PathVariable UUID id) {
        try {
            // Get current Subscriber
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            // Trigger activate_subscription transition
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "activate_subscription");
            logger.info("Subscription activated for Subscriber ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating subscription for Subscriber ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Unsubscribe user
     * PUT /api/subscribers/{id}/unsubscribe
     */
    @PutMapping("/{id}/unsubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> unsubscribe(@PathVariable UUID id) {
        try {
            // Get current Subscriber
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            // Trigger unsubscribe transition
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "unsubscribe");
            logger.info("Subscriber unsubscribed for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unsubscribing Subscriber ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivate subscription
     * PUT /api/subscribers/{id}/reactivate
     */
    @PutMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> reactivateSubscription(@PathVariable UUID id) {
        try {
            // Get current Subscriber
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            // Trigger reactivate transition
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "reactivate");
            logger.info("Subscription reactivated for Subscriber ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reactivating subscription for Subscriber ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subscriber by technical UUID
     * GET /api/subscribers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.getById(id, modelSpec, Subscriber.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Subscriber by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get subscriber by business identifier
     * GET /api/subscribers/business/{subscriberId}
     */
    @GetMapping("/business/{subscriberId}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberByBusinessId(@PathVariable String subscriberId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.findByBusinessId(
                    modelSpec, subscriberId, "subscriberId", Subscriber.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Subscriber by business ID: {}", subscriberId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subscriber by email address
     * GET /api/subscribers/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberByEmail(@PathVariable String email) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.findByBusinessId(
                    modelSpec, email, "email", Subscriber.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Subscriber by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update subscriber preferences
     * PUT /api/subscribers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> updateSubscriber(
            @PathVariable UUID id,
            @RequestBody Subscriber subscriber) {
        try {
            // Validate the updated subscriber
            if (!subscriber.isValid()) {
                logger.warn("Invalid Subscriber update provided: {}", subscriber);
                return ResponseEntity.badRequest().build();
            }

            // Update the Subscriber entity (no transition, just data update)
            EntityWithMetadata<Subscriber> response = entityService.update(id, subscriber, null);
            logger.info("Subscriber updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
