package com.java_template.application.controller;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * SubscriberController - REST endpoints for subscriber management
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
     * Create a new subscriber (sign up)
     * POST /ui/subscriber
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Check for duplicate email
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, subscriber.getEmail(), "email", Subscriber.class);

            if (existing != null) {
                logger.warn("Subscriber with email {} already exists", subscriber.getEmail());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Subscriber already exists with email: %s", subscriber.getEmail())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with email: {}", subscriber.getEmail());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create subscriber: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get subscriber by email
     * GET /ui/subscriber/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberByEmail(@PathVariable String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> response = entityService.findByBusinessId(
                    modelSpec, email, "email", Subscriber.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve subscriber: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Unsubscribe
     * POST /ui/subscriber/{id}/unsubscribe
     */
    @PostMapping("/{id}/unsubscribe")
    public ResponseEntity<EntityWithMetadata<Subscriber>> unsubscribe(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> current = entityService.getById(id, modelSpec, Subscriber.class);
            
            EntityWithMetadata<Subscriber> response = entityService.update(id, current.entity(), "UNSUBSCRIBE");
            logger.info("Subscriber unsubscribed: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to unsubscribe: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

