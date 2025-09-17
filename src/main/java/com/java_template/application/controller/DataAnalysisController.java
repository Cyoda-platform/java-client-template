package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
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
 * DataAnalysisController
 * REST endpoints for managing data analysis operations.
 */
@RestController
@RequestMapping("/api/dataanalysis")
@CrossOrigin(origins = "*")
public class DataAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DataAnalysisController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new data analysis entity
     * POST /api/dataanalysis
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<DataAnalysis>> createDataAnalysis(@RequestBody DataAnalysis entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataAnalysis> response = entityService.create(entity);
            logger.info("DataAnalysis created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating DataAnalysis", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get data analysis by technical UUID
     * GET /api/dataanalysis/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DataAnalysis>> getDataAnalysisById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            EntityWithMetadata<DataAnalysis> response = entityService.getById(id, modelSpec, DataAnalysis.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataAnalysis by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get data analysis by business identifier
     * GET /api/dataanalysis/business/{analysisId}
     */
    @GetMapping("/business/{analysisId}")
    public ResponseEntity<EntityWithMetadata<DataAnalysis>> getDataAnalysisByBusinessId(@PathVariable String analysisId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            EntityWithMetadata<DataAnalysis> response = entityService.findByBusinessId(
                    modelSpec, analysisId, "analysisId", DataAnalysis.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting DataAnalysis by business ID: {}", analysisId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update data analysis with optional workflow transition
     * PUT /api/dataanalysis/{uuid}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DataAnalysis>> updateDataAnalysis(
            @PathVariable UUID id,
            @RequestBody DataAnalysis entity,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataAnalysis> response = entityService.update(id, entity, transition);
            logger.info("DataAnalysis updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating DataAnalysis", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger state transition for data analysis
     * PUT /api/dataanalysis/{uuid}/transition
     */
    @PutMapping("/{id}/transition")
    public ResponseEntity<EntityWithMetadata<DataAnalysis>> triggerTransition(
            @PathVariable UUID id,
            @RequestBody TransitionRequest request) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            EntityWithMetadata<DataAnalysis> current = entityService.getById(id, modelSpec, DataAnalysis.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            DataAnalysis entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DataAnalysis> response = entityService.update(id, entity, request.getTransitionName());
            logger.info("DataAnalysis transition triggered: {} for ID: {}", request.getTransitionName(), id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering transition for DataAnalysis", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete data analysis by technical UUID
     * DELETE /api/dataanalysis/{uuid}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataAnalysis(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("DataAnalysis deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting DataAnalysis", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all data analyses
     * GET /api/dataanalysis
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<DataAnalysis>>> getAllDataAnalyses() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            List<EntityWithMetadata<DataAnalysis>> entities = entityService.findAll(modelSpec, DataAnalysis.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all DataAnalyses", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search data analyses by data source ID
     * GET /api/dataanalysis/search?dataSourceId=text
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<DataAnalysis>>> searchDataAnalysesByDataSourceId(
            @RequestParam String dataSourceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.dataSourceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(dataSourceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataAnalysis>> entities = entityService.search(modelSpec, condition, DataAnalysis.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching DataAnalyses by dataSourceId: {}", dataSourceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get data analyses by analysis type
     * GET /api/dataanalysis/type/{analysisType}
     */
    @GetMapping("/type/{analysisType}")
    public ResponseEntity<List<EntityWithMetadata<DataAnalysis>>> getDataAnalysesByType(
            @PathVariable String analysisType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.analysisType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(analysisType));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataAnalysis>> entities = entityService.search(modelSpec, condition, DataAnalysis.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting DataAnalyses by type: {}", analysisType, e);
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
