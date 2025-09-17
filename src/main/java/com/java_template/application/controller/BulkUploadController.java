package com.java_template.application.controller;

import com.java_template.application.entity.bulkupload.version_1.BulkUpload;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BulkUploadController - REST controller for managing bulk uploads
 * 
 * Handles bulk uploads of HN items from JSON files.
 */
@RestController
@RequestMapping("/api/bulk-upload")
@CrossOrigin(origins = "*")
public class BulkUploadController {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadController.class);
    private final EntityService entityService;

    public BulkUploadController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Upload and process a JSON file containing HN items
     * POST /api/bulk-upload
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Create BulkUpload entity
            BulkUpload bulkUpload = new BulkUpload();
            bulkUpload.setUploadId("upload-" + System.currentTimeMillis());
            bulkUpload.setFileName(file.getOriginalFilename());
            bulkUpload.setFileSize(file.getSize());
            bulkUpload.setUploadTime(System.currentTimeMillis());

            EntityWithMetadata<BulkUpload> response = entityService.create(bulkUpload);
            logger.info("BulkUpload created with ID: {}", response.metadata().getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating BulkUpload", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "UPLOAD_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get bulk upload status and progress
     * GET /api/bulk-upload/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> getBulkUpload(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> response = entityService.getById(uuid, modelSpec, BulkUpload.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting BulkUpload by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retry failed items in a bulk upload
     * PUT /api/bulk-upload/{uuid}?transition=retry
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> retryBulkUpload(
            @PathVariable UUID uuid,
            @RequestBody(required = false) RetryRequest request,
            @RequestParam(required = false) String transition) {
        try {
            // Get existing bulk upload
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> existing = entityService.getById(uuid, modelSpec, BulkUpload.class);

            EntityWithMetadata<BulkUpload> response = entityService.update(uuid, existing.entity(), transition);
            logger.info("BulkUpload retry initiated for ID: {}", uuid);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", response.metadata().getId());
            data.put("entity", response.entity());
            Map<String, Object> meta = new HashMap<>();
            meta.put("state", response.metadata().getState());
            meta.put("createdAt", response.metadata().getCreationDate());
            data.put("meta", meta);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error retrying BulkUpload", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "RETRY_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * List bulk uploads with filtering
     * GET /api/bulk-upload
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listBulkUploads(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            List<EntityWithMetadata<BulkUpload>> entities = entityService.findAll(modelSpec, BulkUpload.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("uploads", entities);
            data.put("total", entities.size());
            data.put("limit", limit);
            data.put("offset", offset);
            result.put("data", data);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error listing BulkUploads", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("code", "LIST_ERROR");
            errorDetails.put("message", e.getMessage());
            error.put("error", errorDetails);
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class RetryRequest {
        private String transition;
    }
}
