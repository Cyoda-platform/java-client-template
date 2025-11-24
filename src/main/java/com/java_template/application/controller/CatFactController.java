package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
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
 * ABOUTME: REST controller for managing cat facts.
 * Provides CRUD operations and search functionality for cat fact entities.
 */
@RestController
@RequestMapping("/ui/catfact")
@CrossOrigin(origins = "*")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<CatFact>> createCatFact(@Valid @RequestBody CatFact catFact) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, catFact.getFactId(), "factId", CatFact.class);

            if (existing != null) {
                logger.warn("CatFact with ID {} already exists", catFact.getFactId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("CatFact already exists with ID: %s", catFact.getFactId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<CatFact> response = entityService.create(catFact);
            logger.info("CatFact created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create cat fact: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<CatFact> response = entityService.getById(id, modelSpec, CatFact.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve cat fact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{factId}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactByBusinessId(
            @PathVariable String factId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<CatFact> response = entityService.findByBusinessId(
                    modelSpec, factId, "factId", CatFact.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve cat fact with business ID '%s': %s", factId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> updateCatFact(
            @PathVariable UUID id,
            @Valid @RequestBody CatFact catFact,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<CatFact> response = entityService.update(id, catFact, transition);
            logger.info("CatFact updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update cat fact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<CatFact>>> listCatFacts(
            Pageable pageable,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (source != null && !source.trim().isEmpty()) {
                SimpleCondition sourceCondition = new SimpleCondition()
                        .withJsonPath("$.source")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(source));
                conditions.add(sourceCondition);
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, CatFact.class, pointInTimeDate));
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                List<EntityWithMetadata<CatFact>> entities = entityService.search(modelSpec, groupCondition, CatFact.class, pointInTimeDate);

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), entities.size());
                List<EntityWithMetadata<CatFact>> pageContent = start < entities.size()
                    ? entities.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<CatFact>> page = new PageImpl<>(pageContent, pageable, entities.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list cat facts: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCatFact(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("CatFact deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete cat fact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

