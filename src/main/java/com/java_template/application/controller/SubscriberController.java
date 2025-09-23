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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SubscriberController - REST controller for subscriber management
 * 
 * Provides endpoints for managing cat fact email subscribers including
 * subscription, unsubscription, and preference management.
 */
@RestController
@RequestMapping("/ui/subscriber")
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
     * Create a new subscriber (subscription)
     * POST /ui/subscriber
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Set creation timestamp
            subscriber.setCreatedAt(LocalDateTime.now());
            subscriber.setUpdatedAt(LocalDateTime.now());
            
            // Set subscription date if not provided
            if (subscriber.getSubscriptionDate() == null) {
                subscriber.setSubscriptionDate(LocalDateTime.now());
            }
            
            // Set active status if not provided
            if (subscriber.getIsActive() == null) {
                subscriber.setIsActive(true);
            }

            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subscriber by technical UUID
     * GET /ui/subscriber/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.getById(id, modelSpec, Subscriber.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Subscriber by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get subscriber by business identifier (subscriberId)
     * GET /ui/subscriber/business/{subscriberId}
     */
    @GetMapping("/business/{subscriberId}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberByBusinessId(@PathVariable String subscriberId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
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
     * Update subscriber with optional workflow transition
     * PUT /ui/subscriber/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> updateSubscriber(
            @PathVariable UUID id,
            @RequestBody Subscriber subscriber,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            subscriber.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Subscriber> response = entityService.update(id, subscriber, transition);
            logger.info("Subscriber updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete subscriber by technical UUID
     * DELETE /ui/subscriber/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Subscriber deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Subscriber", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all subscribers
     * GET /ui/subscriber
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> getAllSubscribers() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            List<EntityWithMetadata<Subscriber>> subscribers = entityService.findAll(modelSpec, Subscriber.class);
            return ResponseEntity.ok(subscribers);
        } catch (Exception e) {
            logger.error("Error getting all Subscribers", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get active subscribers only
     * GET /ui/subscriber/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> getActiveSubscribers() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);

            SimpleCondition activeCondition = new SimpleCondition()
                    .withJsonPath("$.isActive")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) activeCondition));

            List<EntityWithMetadata<Subscriber>> subscribers = entityService.search(modelSpec, condition, Subscriber.class);
            return ResponseEntity.ok(subscribers);
        } catch (Exception e) {
            logger.error("Error getting active Subscribers", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search subscribers by email
     * GET /ui/subscriber/search?email=email@example.com
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> searchSubscribersByEmail(
            @RequestParam String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);

            SimpleCondition emailCondition = new SimpleCondition()
                    .withJsonPath("$.email")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(email.toLowerCase()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) emailCondition));

            List<EntityWithMetadata<Subscriber>> subscribers = entityService.search(modelSpec, condition, Subscriber.class);
            return ResponseEntity.ok(subscribers);
        } catch (Exception e) {
            logger.error("Error searching Subscribers by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Unsubscribe a subscriber (set inactive)
     * POST /ui/subscriber/{id}/unsubscribe
     */
    @PostMapping("/{id}/unsubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> unsubscribeSubscriber(@PathVariable UUID id) {
        try {
            // Get current subscriber
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update to inactive
            Subscriber subscriber = current.entity();
            subscriber.setIsActive(false);
            subscriber.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Subscriber> response = entityService.update(id, subscriber, "unsubscribe");
            logger.info("Subscriber unsubscribed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unsubscribing Subscriber: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Resubscribe a subscriber (set active)
     * POST /ui/subscriber/{id}/resubscribe
     */
    @PostMapping("/{id}/resubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> resubscribeSubscriber(@PathVariable UUID id) {
        try {
            // Get current subscriber
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update to active
            Subscriber subscriber = current.entity();
            subscriber.setIsActive(true);
            subscriber.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Subscriber> response = entityService.update(id, subscriber, "resubscribe");
            logger.info("Subscriber resubscribed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error resubscribing Subscriber: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for subscribers
     * POST /ui/subscriber/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Subscriber>>> advancedSearch(
            @RequestBody SubscriberSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getIsActive() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsActive())));
            }

            if (searchRequest.getEmailDomain() != null && !searchRequest.getEmailDomain().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree("@" + searchRequest.getEmailDomain())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Subscriber>> subscribers = entityService.search(modelSpec, condition, Subscriber.class);
            return ResponseEntity.ok(subscribers);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class SubscriberSearchRequest {
        private String name;
        private Boolean isActive;
        private String emailDomain;
    }
}
