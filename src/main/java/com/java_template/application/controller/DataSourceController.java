package com.java_template.application.controller;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DataSourceController
 * 
 * REST API for managing CSV data sources and triggering data processing workflows.
 */
@RestController
@RequestMapping("/api/datasources")
@CrossOrigin(origins = "*")
public class DataSourceController {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);
    private final EntityService entityService;

    public DataSourceController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new data source
     * POST /api/datasources
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<DataSource>> createDataSource(@RequestBody DataSource dataSource) {
        try {
            // Generate unique dataSourceId if not provided
            if (dataSource.getDataSourceId() == null || dataSource.getDataSourceId().trim().isEmpty()) {
                dataSource.setDataSourceId("ds-" + UUID.randomUUID().toString().substring(0, 8));
            }

            // Validate required fields
            if (!dataSource.isValid()) {
                logger.warn("Invalid DataSource provided: {}", dataSource);
                return ResponseEntity.badRequest().build();
            }

            // Create the DataSource entity (creates in initial_state, then auto-transitions to created)
            EntityWithMetadata<DataSource> response = entityService.create(dataSource);
            logger.info("DataSource created with ID: {} and business ID: {}", 
                       response.metadata().getId(), dataSource.getDataSourceId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating DataSource", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger data fetch operation
     * PUT /api/datasources/{id}/fetch
     */
    @PutMapping("/{id}/fetch")
    public ResponseEntity<EntityWithMetadata<DataSource>> fetchData(@PathVariable UUID id) {
        try {
            // Get current DataSource
            ModelSpec modelSpec = new ModelSpec()
                    .withName(DataSource.ENTITY_NAME)
                    .withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> current = entityService.getById(id, modelSpec, DataSource.class);
            
            // Trigger start_fetch transition
            EntityWithMetadata<DataSource> response = entityService.update(id, current.entity(), "start_fetch");
            logger.info("Data fetch triggered for DataSource ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering data fetch for DataSource ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Refresh data from source
     * PUT /api/datasources/{id}/refresh
     */
    @PutMapping("/{id}/refresh")
    public ResponseEntity<EntityWithMetadata<DataSource>> refreshData(@PathVariable UUID id) {
        try {
            // Get current DataSource
            ModelSpec modelSpec = new ModelSpec()
                    .withName(DataSource.ENTITY_NAME)
                    .withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> current = entityService.getById(id, modelSpec, DataSource.class);
            
            // Trigger refresh_data transition
            EntityWithMetadata<DataSource> response = entityService.update(id, current.entity(), "refresh_data");
            logger.info("Data refresh triggered for DataSource ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering data refresh for DataSource ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get data source by technical UUID
     * GET /api/datasources/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DataSource>> getDataSourceById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(DataSource.ENTITY_NAME)
                    .withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> response = entityService.getById(id, modelSpec, DataSource.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataSource by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get data source by business identifier
     * GET /api/datasources/business/{dataSourceId}
     */
    @GetMapping("/business/{dataSourceId}")
    public ResponseEntity<EntityWithMetadata<DataSource>> getDataSourceByBusinessId(@PathVariable String dataSourceId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(DataSource.ENTITY_NAME)
                    .withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> response = entityService.findByBusinessId(
                    modelSpec, dataSourceId, "dataSourceId", DataSource.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataSource by business ID: {}", dataSourceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry fetch operation for failed data sources
     * PUT /api/datasources/{id}/retry
     */
    @PutMapping("/{id}/retry")
    public ResponseEntity<EntityWithMetadata<DataSource>> retryFetch(@PathVariable UUID id) {
        try {
            // Get current DataSource
            ModelSpec modelSpec = new ModelSpec()
                    .withName(DataSource.ENTITY_NAME)
                    .withVersion(DataSource.ENTITY_VERSION);
            EntityWithMetadata<DataSource> current = entityService.getById(id, modelSpec, DataSource.class);
            
            // Trigger retry_fetch transition (only valid from failed state)
            EntityWithMetadata<DataSource> response = entityService.update(id, current.entity(), "retry_fetch");
            logger.info("Retry fetch triggered for DataSource ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering retry fetch for DataSource ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
