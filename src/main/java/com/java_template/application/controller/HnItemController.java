package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HnItem;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HnItemController - REST API endpoints for managing Hacker News items
 * 
 * Provides endpoints for:
 * - Single item operations (CRUD)
 * - Bulk operations (array and file upload)
 * - Firebase API integration
 * - Hierarchical search capabilities
 */
@RestController
@RequestMapping("/api/hnitem")
@CrossOrigin(origins = "*")
public class HnItemController {

    private static final Logger logger = LoggerFactory.getLogger(HnItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HnItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a single HN item
     * POST /api/hnitem
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HnItem>> createEntity(@RequestBody HnItem entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<HnItem> response = entityService.create(entity);
            logger.info("HnItem created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating HnItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create multiple HN items from an array
     * POST /api/hnitem/bulk
     */
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateResponse> createMultipleEntities(@RequestBody List<HnItem> entities) {
        try {
            BulkCreateResponse response = new BulkCreateResponse();
            response.setItems(new ArrayList<>());
            
            for (HnItem entity : entities) {
                try {
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    
                    EntityWithMetadata<HnItem> created = entityService.create(entity);
                    response.getItems().add(created);
                    response.setCreated(response.getCreated() + 1);
                } catch (Exception e) {
                    logger.error("Error creating HnItem in bulk: {}", entity.getId(), e);
                    response.setFailed(response.getFailed() + 1);
                }
            }
            
            logger.info("Bulk create completed: {} created, {} failed", response.getCreated(), response.getFailed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in bulk create", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload HN items from a JSON file
     * POST /api/hnitem/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFromFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Parse JSON file to list of HnItem objects
            HnItem[] itemsArray = objectMapper.readValue(file.getInputStream(), HnItem[].class);
            List<HnItem> items = List.of(itemsArray);
            
            FileUploadResponse response = new FileUploadResponse();
            response.setErrors(new ArrayList<>());
            
            for (HnItem entity : items) {
                try {
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    
                    entityService.create(entity);
                    response.setCreated(response.getCreated() + 1);
                } catch (Exception e) {
                    logger.error("Error creating HnItem from file: {}", entity.getId(), e);
                    response.setFailed(response.getFailed() + 1);
                    
                    FileUploadError error = new FileUploadError();
                    error.setItem(entity);
                    error.setError(e.getMessage());
                    response.getErrors().add(error);
                }
            }
            
            logger.info("File upload completed: {} created, {} failed", response.getCreated(), response.getFailed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing file upload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get HN item by technical UUID
     * GET /api/hnitem/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getEntityById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.getById(id, modelSpec, HnItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get HN item by business ID (Hacker News ID)
     * GET /api/hnitem/business/{hnId}
     */
    @GetMapping("/business/{hnId}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getEntityByBusinessId(@PathVariable Long hnId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.findByBusinessId(
                    modelSpec, hnId.toString(), "id", HnItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by business ID: {}", hnId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update HN item with optional workflow transition
     * PUT /api/hnitem/{id}?transition={transitionName}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HnItem>> updateEntity(
            @PathVariable UUID id,
            @RequestBody HnItem entity,
            @RequestParam(required = false) String transition) {
        try {
            entity.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<HnItem> response = entityService.update(id, entity, transition);
            logger.info("HnItem updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating HnItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete HN item by technical UUID
     * DELETE /api/hnitem/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("HnItem deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting HnItem", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Response DTOs
    @Getter
    @Setter
    public static class BulkCreateResponse {
        private int created = 0;
        private int failed = 0;
        private List<EntityWithMetadata<HnItem>> items;
    }

    @Getter
    @Setter
    public static class FileUploadResponse {
        private int created = 0;
        private int failed = 0;
        private List<FileUploadError> errors;
    }

    @Getter
    @Setter
    public static class FileUploadError {
        private HnItem item;
        private String error;
    }
}
