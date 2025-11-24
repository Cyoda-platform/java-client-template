package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.interaction.version_1.Interaction;
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
 * ABOUTME: REST controller for managing interactions.
 * Provides CRUD operations and search functionality for interaction entities.
 */
@RestController
@RequestMapping("/ui/interaction")
@CrossOrigin(origins = "*")
public class InteractionController {

    private static final Logger logger = LoggerFactory.getLogger(InteractionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InteractionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Interaction>> createInteraction(@Valid @RequestBody Interaction interaction) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Interaction.ENTITY_NAME).withVersion(Interaction.ENTITY_VERSION);
            EntityWithMetadata<Interaction> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, interaction.getInteractionId(), "interactionId", Interaction.class);

            if (existing != null) {
                logger.warn("Interaction with ID {} already exists", interaction.getInteractionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Interaction already exists with ID: %s", interaction.getInteractionId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Interaction> response = entityService.create(interaction);
            logger.info("Interaction created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create interaction: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Interaction>> getInteractionById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Interaction.ENTITY_NAME).withVersion(Interaction.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Interaction> response = entityService.getById(id, modelSpec, Interaction.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve interaction with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{interactionId}")
    public ResponseEntity<EntityWithMetadata<Interaction>> getInteractionByBusinessId(
            @PathVariable String interactionId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Interaction.ENTITY_NAME).withVersion(Interaction.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Interaction> response = entityService.findByBusinessId(
                    modelSpec, interactionId, "interactionId", Interaction.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve interaction with business ID '%s': %s", interactionId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Interaction>> updateInteraction(
            @PathVariable UUID id,
            @Valid @RequestBody Interaction interaction,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Interaction> response = entityService.update(id, interaction, transition);
            logger.info("Interaction updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update interaction with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Interaction>>> listInteractions(
            Pageable pageable,
            @RequestParam(required = false) String subscriberId,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String interactionType,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Interaction.ENTITY_NAME).withVersion(Interaction.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (subscriberId != null && !subscriberId.trim().isEmpty()) {
                SimpleCondition subCondition = new SimpleCondition()
                        .withJsonPath("$.subscriberId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(subscriberId));
                conditions.add(subCondition);
            }

            if (campaignId != null && !campaignId.trim().isEmpty()) {
                SimpleCondition campCondition = new SimpleCondition()
                        .withJsonPath("$.campaignId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(campaignId));
                conditions.add(campCondition);
            }

            if (interactionType != null && !interactionType.trim().isEmpty()) {
                SimpleCondition typeCondition = new SimpleCondition()
                        .withJsonPath("$.interactionType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(interactionType));
                conditions.add(typeCondition);
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Interaction.class, pointInTimeDate));
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                List<EntityWithMetadata<Interaction>> entities = entityService.search(modelSpec, groupCondition, Interaction.class, pointInTimeDate);

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), entities.size());
                List<EntityWithMetadata<Interaction>> pageContent = start < entities.size()
                    ? entities.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Interaction>> page = new PageImpl<>(pageContent, pageable, entities.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list interactions: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInteraction(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Interaction deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete interaction with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

