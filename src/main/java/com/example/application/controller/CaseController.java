package com.example.application.controller;

import com.example.application.entity.case_entity.version_1.Case;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Stream;

/**
 * REST Controller for Case Entity
 * Provides CRUD operations and search endpoints for managing compliance cases
 */
@RestController
@RequestMapping("/ui/case")
@CrossOrigin(origins = "*")
public class CaseController {

    private static final Logger logger = LoggerFactory.getLogger(CaseController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CaseController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Case>> createEntity(@Valid @RequestBody Case entity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            EntityWithMetadata<Case> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, entity.getId(), "id", Case.class);

            if (existing != null) {
                logger.warn("Case with business ID {} already exists", entity.getId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Case already exists with ID: %s", entity.getId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Case> response = entityService.create(entity);
            logger.info("Case created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create entity: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Case>> getEntityById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Case> response = entityService.getById(id, modelSpec, Case.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{caseId}")
    public ResponseEntity<EntityWithMetadata<Case>> getEntityByBusinessId(
            @PathVariable String caseId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Case> response = entityService.findByBusinessId(
                    modelSpec, caseId, "id", Case.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve entity with business ID '%s': %s", caseId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getEntityChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            List<EntityChangeMeta> changes = entityService.getEntityChangesMetadata(id, pointInTimeDate);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve change history for entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Case>> updateEntity(
            @PathVariable UUID id,
            @Valid @RequestBody Case entity,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Case> response = entityService.update(id, entity, transition);
            logger.info("Case updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<PageResult<EntityWithMetadata<Case>>> searchWithPagination(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID searchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (title != null && !title.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.title")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(title)));
            }

            if (status != null && !status.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(status)));
            }

            SearchAndRetrievalParams paginationParams = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .pointInTime(pointInTimeDate)
                    .searchId(searchId)
                    .build();

            PageResult<EntityWithMetadata<Case>> pageResult;
            if (conditions.isEmpty()) {
                pageResult = entityService.findAll(modelSpec, Case.class, paginationParams);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                pageResult = entityService.search(modelSpec, condition, Case.class, paginationParams);
            }

            logger.info("Search returned page {} of {} (total: {} entities, searchId: {})",
                    pageResult.pageNumber(), pageResult.totalPages(), pageResult.totalElements(), pageResult.searchId());

            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search entities: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/export")
    public ResponseEntity<String> exportEntities(
            @RequestBody(required = false) SearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            Date pointInTimeDate = searchRequest != null && searchRequest.getPointInTime() != null
                ? Date.from(searchRequest.getPointInTime().toInstant())
                : null;

            GroupCondition condition = null;
            if (searchRequest != null && searchRequest.getTitle() != null) {
                List<QueryCondition> conditions = new ArrayList<>();
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.title")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getTitle())));
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            long count;
            if (condition == null) {
                try (Stream<EntityWithMetadata<Case>> stream =
                        entityService.streamAll(modelSpec, Case.class,
                                SearchAndRetrievalParams.builder()
                                        .pageSize(100)
                                        .pointInTime(pointInTimeDate)
                                        .build())) {
                    count = stream.count();
                }
            } else {
                try (Stream<EntityWithMetadata<Case>> stream =
                        entityService.searchAsStream(modelSpec, condition, Case.class,
                                SearchAndRetrievalParams.builder()
                                        .pageSize(100)
                                        .inMemory(false)
                                        .pointInTime(pointInTimeDate)
                                        .build())) {
                    count = stream.count();
                }
            }

            logger.info("Exported {} entities", count);
            return ResponseEntity.ok(String.format("Exported %d entities", count));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to export entities: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Case deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/business/{caseId}")
    public ResponseEntity<Void> deleteEntityByBusinessId(@PathVariable String caseId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, caseId, "id", Case.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Case deleted with business ID: {}", caseId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with business ID '%s': %s", caseId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAllEntities() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Case.ENTITY_NAME).withVersion(Case.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Cases - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d entities", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all entities: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class SearchRequest {
        private String title;
        private OffsetDateTime pointInTime;
    }
}

