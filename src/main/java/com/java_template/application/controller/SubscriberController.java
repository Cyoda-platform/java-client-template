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
 * 
 * Base Path: /api/subscribers
 * 
 * Provides endpoints for:
 * - Subscriber registration
 * - Subscriber activation
 * - Subscriber unsubscription
 * - Subscriber reactivation
 * - Subscriber resubscription
 * - Subscriber listing and search
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
     * Register new subscriber
     * POST /api/subscribers
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@RequestBody SubscriberRegistrationRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setFirstName(request.getFirstName());
            subscriber.setLastName(request.getLastName());
            subscriber.setSubscriptionDate(LocalDateTime.now());
            subscriber.setIsActive(true);
            subscriber.setPreferences(request.getPreferences());
            subscriber.setTotalEmailsReceived(0);
            subscriber.setUnsubscribeToken(UUID.randomUUID().toString());

            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate pending subscriber
     * PUT /api/subscribers/{uuid}/activate
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> activateSubscriber(
            @PathVariable UUID id,
            @RequestBody SubscriberTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_active");
            logger.info("Subscriber activated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Unsubscribe active subscriber
     * PUT /api/subscribers/{uuid}/unsubscribe
     */
    @PutMapping("/{id}/unsubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> unsubscribeSubscriber(
            @PathVariable UUID id,
            @RequestBody SubscriberTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_unsubscribed");
            logger.info("Subscriber unsubscribed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unsubscribing subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivate bounced subscriber
     * PUT /api/subscribers/{uuid}/reactivate
     */
    @PutMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Subscriber>> reactivateSubscriber(
            @PathVariable UUID id,
            @RequestBody SubscriberTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            // Update email if provided
            if (request.getNewEmail() != null && !request.getNewEmail().trim().isEmpty()) {
                current.entity().setEmail(request.getNewEmail());
            }
            
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_active");
            logger.info("Subscriber reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reactivating subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Resubscribe unsubscribed user
     * PUT /api/subscribers/{uuid}/resubscribe
     */
    @PutMapping("/{id}/resubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> resubscribeSubscriber(
            @PathVariable UUID id,
            @RequestBody SubscriberTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "transition_to_active");
            logger.info("Subscriber resubscribed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error resubscribing subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subscriber by UUID
     * GET /api/subscribers/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.getById(id, modelSpec, Subscriber.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting subscriber by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all subscribers with filtering
     * GET /api/subscribers
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> getAllSubscribers(
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            
            if (state != null && !state.trim().isEmpty()) {
                // Filter by state using search
                SimpleCondition stateCondition = new SimpleCondition()
                        .withJsonPath("$.meta.state")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(state));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(stateCondition));

                List<EntityWithMetadata<Subscriber>> subscribers = entityService.search(modelSpec, condition, Subscriber.class);
                return ResponseEntity.ok(subscribers);
            } else {
                List<EntityWithMetadata<Subscriber>> subscribers = entityService.findAll(modelSpec, Subscriber.class);
                return ResponseEntity.ok(subscribers);
            }
        } catch (Exception e) {
            logger.error("Error getting all subscribers", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class SubscriberRegistrationRequest {
        private String email;
        private String firstName;
        private String lastName;
        private Map<String, Object> preferences;
    }

    @Getter
    @Setter
    public static class SubscriberTransitionRequest {
        private String transitionName;
        private String confirmationToken;
        private String unsubscribeToken;
        private String reason;
        private String newEmail;
        private Boolean confirmOptIn;
    }
}
