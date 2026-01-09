package com.example.application.controller;

import com.example.application.entity.watchlist_entry.version_1.WatchlistEntry;
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
 * REST Controller for WatchlistEntry Entity
 * Provides CRUD operations and search endpoints for managing watchlist entries
 */
@RestController
@RequestMapping("/ui/watchlist-entry")
@CrossOrigin(origins = "*")
public class WatchlistEntryController {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistEntryController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public WatchlistEntryController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<WatchlistEntry>> createEntity(@Valid @RequestBody WatchlistEntry entity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            EntityWithMetadata<WatchlistEntry> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, entity.getId(), "id", WatchlistEntry.class);

            if (existing != null) {
                logger.warn("WatchlistEntry with business ID {} already exists", entity.getId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("WatchlistEntry already exists with ID: %s", entity.getId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<WatchlistEntry> response = entityService.create(entity);
            logger.info("WatchlistEntry created with ID: {}", response.metadata().getId());

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
    public ResponseEntity<EntityWithMetadata<WatchlistEntry>> getEntityById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<WatchlistEntry> response = entityService.getById(id, modelSpec, WatchlistEntry.class, pointInTimeDate);
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

    @GetMapping("/business/{watchlistEntryId}")
    public ResponseEntity<EntityWithMetadata<WatchlistEntry>> getEntityByBusinessId(
            @PathVariable String watchlistEntryId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<WatchlistEntry> response = entityService.findByBusinessId(
                    modelSpec, watchlistEntryId, "id", WatchlistEntry.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve entity with business ID '%s': %s", watchlistEntryId, e.getMessage())
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
    public ResponseEntity<EntityWithMetadata<WatchlistEntry>> updateEntity(
            @PathVariable UUID id,
            @Valid @RequestBody WatchlistEntry entity,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<WatchlistEntry> response = entityService.update(id, entity, transition);
            logger.info("WatchlistEntry updated with ID: {}", id);
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
    public ResponseEntity<PageResult<EntityWithMetadata<WatchlistEntry>>> searchWithPagination(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID searchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (name != null && !name.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(name)));
            }

            if (source != null && !source.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.source")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(source)));
            }

            SearchAndRetrievalParams paginationParams = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .pointInTime(pointInTimeDate)
                    .searchId(searchId)
                    .build();

            PageResult<EntityWithMetadata<WatchlistEntry>> pageResult;
            if (conditions.isEmpty()) {
                pageResult = entityService.findAll(modelSpec, WatchlistEntry.class, paginationParams);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                pageResult = entityService.search(modelSpec, condition, WatchlistEntry.class, paginationParams);
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
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            Date pointInTimeDate = searchRequest != null && searchRequest.getPointInTime() != null
                ? Date.from(searchRequest.getPointInTime().toInstant())
                : null;

            GroupCondition condition = null;
            if (searchRequest != null && searchRequest.getName() != null) {
                List<QueryCondition> conditions = new ArrayList<>();
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            long count;
            if (condition == null) {
                try (Stream<EntityWithMetadata<WatchlistEntry>> stream =
                        entityService.streamAll(modelSpec, WatchlistEntry.class,
                                SearchAndRetrievalParams.builder()
                                        .pageSize(100)
                                        .pointInTime(pointInTimeDate)
                                        .build())) {
                    count = stream.count();
                }
            } else {
                try (Stream<EntityWithMetadata<WatchlistEntry>> stream =
                        entityService.searchAsStream(modelSpec, condition, WatchlistEntry.class,
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
            logger.info("WatchlistEntry deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/business/{watchlistEntryId}")
    public ResponseEntity<Void> deleteEntityByBusinessId(@PathVariable String watchlistEntryId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, watchlistEntryId, "id", WatchlistEntry.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("WatchlistEntry deleted with business ID: {}", watchlistEntryId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete entity with business ID '%s': %s", watchlistEntryId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAllEntities() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(WatchlistEntry.ENTITY_NAME).withVersion(WatchlistEntry.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all WatchlistEntries - count: {}", deletedCount);
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
        private String name;
        private OffsetDateTime pointInTime;
    }
}

