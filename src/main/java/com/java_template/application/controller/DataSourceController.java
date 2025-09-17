package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.datasource.version_1.DataSource;
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
import java.util.UUID;

/**
 * DataSourceController
 * REST endpoints for managing data source downloads.
 */
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin(origins = "*")
public class DataSourceController {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DataSourceController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new data source and initiate download
     * POST /api/datasource
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<DataSource>> createDataSource(@RequestBody DataSource entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataSource> response = entityService.create(entity);
            logger.info("DataSource created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating DataSource", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get data source by technical UUID
     * GET /api/datasource/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DataSource>> getDataSourceById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> response = entityService.getById(id, modelSpec, DataSource.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataSource by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get data source by business identifier
     * GET /api/datasource/business/{sourceId}
     */
    @GetMapping("/business/{sourceId}")
    public ResponseEntity<EntityWithMetadata<DataSource>> getDataSourceByBusinessId(@PathVariable String sourceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> response = entityService.findByBusinessId(
                    modelSpec, sourceId, "sourceId", DataSource.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataSource by business ID: {}", sourceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update data source with optional workflow transition
     * PUT /api/datasource/{uuid}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DataSource>> updateDataSource(
            @PathVariable UUID id,
            @RequestBody DataSource entity,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataSource> response = entityService.update(id, entity, transition);
            logger.info("DataSource updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating DataSource", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger state transition for data source
     * PUT /api/datasource/{uuid}/transition
     */
    @PutMapping("/{id}/transition")
    public ResponseEntity<EntityWithMetadata<DataSource>> triggerTransition(
            @PathVariable UUID id,
            @RequestBody TransitionRequest request) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> current = entityService.getById(id, modelSpec, DataSource.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            DataSource entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataSource> response = entityService.update(id, entity, request.getTransitionName());
            logger.info("DataSource transition triggered: {} for ID: {}", request.getTransitionName(), id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering transition for DataSource", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete data source by technical UUID
     * DELETE /api/datasource/{uuid}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("DataSource deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting DataSource", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all data sources
     * GET /api/datasource
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<DataSource>>> getAllDataSources() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            List<EntityWithMetadata<DataSource>> entities = entityService.findAll(modelSpec, DataSource.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all DataSources", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search data sources by URL
     * GET /api/datasource/search?url=text
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<DataSource>>> searchDataSourcesByUrl(
            @RequestParam String url) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.url")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(url));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataSource>> entities = entityService.search(modelSpec, condition, DataSource.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching DataSources by URL: {}", url, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for transition requests
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transitionName;
    }
}
