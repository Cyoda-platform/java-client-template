package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for managing subscribers.
 * Provides CRUD operations and search functionality for subscriber entities.
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

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subscriber>> createSubscriber(@Valid @RequestBody Subscriber subscriber) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            EntityWithMetadata<Subscriber> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, subscriber.getSubscriberId(), "subscriberId", Subscriber.class);

            if (existing != null) {
                logger.warn("Subscriber with ID {} already exists", subscriber.getSubscriberId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Subscriber already exists with ID: %s", subscriber.getSubscriberId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Subscriber> response = entityService.create(subscriber);
            logger.info("Subscriber created with ID: {}", response.metadata().getId());

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

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Subscriber> response = entityService.getById(id, modelSpec, Subscriber.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve subscriber with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{subscriberId}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> getSubscriberByBusinessId(
            @PathVariable String subscriberId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Subscriber> response = entityService.findByBusinessId(
                    modelSpec, subscriberId, "subscriberId", Subscriber.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve subscriber with business ID '%s': %s", subscriberId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subscriber>> updateSubscriber(
            @PathVariable UUID id,
            @Valid @RequestBody Subscriber subscriber,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Subscriber> response = entityService.update(id, subscriber, transition);
            logger.info("Subscriber updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update subscriber with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Subscriber>>> listSubscribers(
            Pageable pageable,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (email != null && !email.trim().isEmpty()) {
                SimpleCondition emailCondition = new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(email));
                conditions.add(emailCondition);
            }

            if (isActive != null) {
                SimpleCondition activeCondition = new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(isActive));
                conditions.add(activeCondition);
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Subscriber.class, pointInTimeDate));
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                List<EntityWithMetadata<Subscriber>> entities = entityService.search(modelSpec, groupCondition, Subscriber.class, pointInTimeDate);

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), entities.size());
                List<EntityWithMetadata<Subscriber>> pageContent = start < entities.size()
                    ? entities.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Subscriber>> page = new PageImpl<>(pageContent, pageable, entities.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list subscribers: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Subscriber deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete subscriber with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

