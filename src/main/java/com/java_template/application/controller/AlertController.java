package com.java_template.application.controller;

import com.example.application.entity.alert.version_1.Alert;
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
 * REST Controller for Alert Entity
 * Provides CRUD operations and search endpoints for managing alerts
 */
@RestController
@RequestMapping("/ui/alert")
@CrossOrigin(origins = "*")
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AlertController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Alert>> createEntity(@Valid @RequestBody Alert entity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            EntityWithMetadata<Alert> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, entity.getId(), "id", Alert.class);

            if (existing != null) {
                logger.warn("Alert with business ID {} already exists", entity.getId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Alert already exists with ID: %s", entity.getId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Alert> response = entityService.create(entity);
            logger.info("Alert created with ID: {}", response.metadata().getId());

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
    public ResponseEntity<EntityWithMetadata<Alert>> getEntityById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Alert> response = entityService.getById(id, modelSpec, Alert.class, pointInTimeDate);
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

    @GetMapping("/business/{alertId}")
    public ResponseEntity<EntityWithMetadata<Alert>> getEntityByBusinessId(
            @PathVariable String alertId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Alert> response = entityService.findByBusinessId(
                    modelSpec, alertId, "id", Alert.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve entity with business ID '%s': %s", alertId, e.getMessage())
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
    public ResponseEntity<EntityWithMetadata<Alert>> updateEntity(
            @PathVariable UUID id,
            @Valid @RequestBody Alert entity,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Alert> response = entityService.update(id, entity, transition);
            logger.info("Alert updated with ID: {}", id);
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
    public ResponseEntity<PageResult<EntityWithMetadata<Alert>>> searchWithPagination(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID searchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (customerId != null && !customerId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.customerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(customerId)));
            }

            if (severity != null && !severity.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.severity")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(severity)));
            }

            SearchAndRetrievalParams paginationParams = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .pointInTime(pointInTimeDate)
                    .searchId(searchId)
                    .build();

            PageResult<EntityWithMetadata<Alert>> pageResult;
            if (conditions.isEmpty()) {
                pageResult = entityService.findAll(modelSpec, Alert.class, paginationParams);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                pageResult = entityService.search(modelSpec, condition, Alert.class, paginationParams);
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
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            Date pointInTimeDate = searchRequest != null && searchRequest.getPointInTime() != null
                ? Date.from(searchRequest.getPointInTime().toInstant())
                : null;

            GroupCondition condition = null;
            if (searchRequest != null && searchRequest.getCustomerId() != null) {
                List<QueryCondition> conditions = new ArrayList<>();
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.customerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCustomerId())));
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            long count;
            if (condition == null) {
                try (Stream<EntityWithMetadata<Alert>> stream =
                        entityService.streamAll(modelSpec, Alert.class,
                                SearchAndRetrievalParams.builder()
                                        .pageSize(100)
                                        .pointInTime(pointInTimeDate)
                                        .build())) {
                    count = stream.count();
                }
            } else {
                try (Stream<EntityWithMetadata<Alert>> stream =
                        entityService.searchAsStream(modelSpec, condition, Alert.class,
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
            logger.info("Alert deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/business/{alertId}")
    public ResponseEntity<Void> deleteEntityByBusinessId(@PathVariable String alertId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, alertId, "id", Alert.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Alert deleted with business ID: {}", alertId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with business ID '%s': %s", alertId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAllEntities() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Alert.ENTITY_NAME).withVersion(Alert.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all Alerts - count: {}", deletedCount);
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
        private String customerId;
        private OffsetDateTime pointInTime;
    }
}

