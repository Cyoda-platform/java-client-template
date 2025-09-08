package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import java.util.Map;
import java.util.UUID;

/**
 * SubscriberController - REST API controller for subscriber management
 * Base Path: /api/subscribers
 */
@RestController
@RequestMapping("/api/subscribers")
@CrossOrigin(origins = "*")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new subscriber subscription
     * POST /api/subscribers
     * Transition: INITIAL → PENDING (SubscriberRegistrationProcessor)
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Set subscription date to current timestamp
            subscriber.setSubscriptionDate(LocalDateTime.now());
            subscriber.setIsActive(true);

            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate a pending subscription
     * PUT /api/subscribers/{id}/activate
     * Transition: PENDING → ACTIVE (SubscriberActivationProcessor)
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> activateSubscriber(
            @PathVariable UUID id,
            @RequestBody ActivationRequest request) {
        try {
            // Get current subscriber
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update with transition
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_active");
            logger.info("Subscriber {} activated", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Temporarily deactivate an active subscription
     * PUT /api/subscribers/{id}/deactivate
     * Transition: ACTIVE → INACTIVE (SubscriberDeactivationProcessor)
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> deactivateSubscriber(
            @PathVariable UUID id,
            @RequestBody DeactivationRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_inactive");
            logger.info("Subscriber {} deactivated", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deactivating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivate an inactive subscription
     * PUT /api/subscribers/{id}/reactivate
     * Transition: INACTIVE → ACTIVE (SubscriberReactivationProcessor)
     */
    @PutMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> reactivateSubscriber(
            @PathVariable UUID id,
            @RequestBody ReactivationRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_active");
            logger.info("Subscriber {} reactivated", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reactivating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Permanently unsubscribe
     * PUT /api/subscribers/{id}/unsubscribe
     * Transition: ANY → UNSUBSCRIBED (SubscriberUnsubscribeProcessor)
     */
    @PutMapping("/{id}/unsubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> unsubscribeSubscriber(
            @PathVariable UUID id,
            @RequestBody UnsubscribeRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_unsubscribed");
            logger.info("Subscriber {} unsubscribed", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unsubscribing Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subscriber details
     * GET /api/subscribers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriber(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Subscriber by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all subscribers with filtering
     * GET /api/subscribers
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> getAllSubscribers(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isActive) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            
            if (isActive != null) {
                SimpleCondition activeCondition = new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(isActive));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(activeCondition));

                List<EntityWithMetadata<Subscriber>> entities = entityService.search(modelSpec, condition, Subscriber.class);
                return ResponseEntity.ok(entities);
            } else {
                List<EntityWithMetadata<Subscriber>> entities = entityService.findAll(modelSpec, Subscriber.class);
                return ResponseEntity.ok(entities);
            }
        } catch (Exception e) {
            logger.error("Error getting all Subscribers", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class ActivationRequest {
        private String confirmationToken;
        private String transitionName = "activate";
    }

    @Getter
    @Setter
    public static class DeactivationRequest {
        private String reason;
        private String transitionName = "deactivate";
    }

    @Getter
    @Setter
    public static class ReactivationRequest {
        private String transitionName = "reactivate";
    }

    @Getter
    @Setter
    public static class UnsubscribeRequest {
        private String reason;
        private String transitionName = "unsubscribe";
    }
}
