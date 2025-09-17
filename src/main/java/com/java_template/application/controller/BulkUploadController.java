package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.bulkupload.version_1.BulkUpload;
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
 * BulkUploadController - REST API endpoints for managing bulk upload operations
 * 
 * Provides endpoints for uploading JSON files containing HN items,
 * tracking upload status, and managing bulk processing operations.
 */
@RestController
@RequestMapping("/api/bulkupload")
@CrossOrigin(origins = "*")
public class BulkUploadController {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public BulkUploadController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload JSON file containing HN items for bulk processing
     * POST /api/bulkupload
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<BulkUpload>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Create BulkUpload entity
            BulkUpload entity = new BulkUpload();
            entity.setUploadId("upload-" + UUID.randomUUID().toString());
            entity.setFileName(file.getOriginalFilename());
            entity.setFileSize(file.getSize());
            entity.setUploadedAt(LocalDateTime.now());
            entity.setUploadedBy(uploadedBy);
            entity.setTotalItems(0);
            entity.setProcessedItems(0);
            entity.setFailedItems(0);
            entity.setErrorMessages(new ArrayList<>());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            // In a real implementation, save the file to storage here
            // String filePath = saveFileToStorage(file, entity.getUploadId());

            EntityWithMetadata<BulkUpload> response = entityService.create(entity);
            logger.info("BulkUpload created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading file", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get upload status by technical UUID
     * GET /api/bulkupload/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<BulkUpload>> getUploadById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> response = entityService.getById(id, modelSpec, BulkUpload.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting BulkUpload by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get upload by business identifier (uploadId)
     * GET /api/bulkupload/upload/{uploadId}
     */
    @GetMapping("/upload/{uploadId}")
    public ResponseEntity<EntityWithMetadata<BulkUpload>> getUploadByUploadId(@PathVariable String uploadId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> response = entityService.findByBusinessId(
                    modelSpec, uploadId, "uploadId", BulkUpload.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting BulkUpload by upload ID: {}", uploadId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Manually trigger processing of an uploaded file
     * POST /api/bulkupload/{id}/process?transition=start_processing
     */
    @PostMapping("/{id}/process")
    public ResponseEntity<EntityWithMetadata<BulkUpload>> startProcessing(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> current = entityService.getById(id, modelSpec, BulkUpload.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            BulkUpload entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<BulkUpload> response = entityService.update(id, entity, transition);
            logger.info("BulkUpload processing started for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting processing for BulkUpload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry a failed bulk upload operation
     * POST /api/bulkupload/{id}/retry?transition=retry_upload
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<EntityWithMetadata<BulkUpload>> retryUpload(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> current = entityService.getById(id, modelSpec, BulkUpload.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            BulkUpload entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<BulkUpload> response = entityService.update(id, entity, transition);
            logger.info("BulkUpload retry initiated for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrying BulkUpload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reprocess failed items from a completed upload with errors
     * POST /api/bulkupload/{id}/reprocess?transition=reprocess_failed
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<EntityWithMetadata<BulkUpload>> reprocessFailed(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> current = entityService.getById(id, modelSpec, BulkUpload.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            BulkUpload entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<BulkUpload> response = entityService.update(id, entity, transition);
            logger.info("BulkUpload reprocess initiated for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reprocessing BulkUpload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a bulk upload record
     * DELETE /api/bulkupload/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUpload(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("BulkUpload deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting BulkUpload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all bulk upload operations (paginated)
     * GET /api/bulkupload
     */
    @GetMapping
    public ResponseEntity<UploadListResponse> getAllUploads(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploadedBy,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            List<SimpleCondition> conditions = new ArrayList<>();

            // Filter by uploadedBy
            if (uploadedBy != null && !uploadedBy.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.uploadedBy")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(uploadedBy)));
            }

            List<EntityWithMetadata<BulkUpload>> entities;
            if (conditions.isEmpty()) {
                entities = entityService.findAll(modelSpec, BulkUpload.class);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                entities = entityService.search(modelSpec, condition, BulkUpload.class);
            }

            // Filter by status (entity state) if provided
            if (status != null && !status.trim().isEmpty()) {
                entities = entities.stream()
                        .filter(e -> status.equals(e.metadata().getState()))
                        .toList();
            }

            // Apply pagination
            int total = entities.size();
            int endIndex = Math.min(offset + limit, total);
            List<EntityWithMetadata<BulkUpload>> paginatedEntities = entities.subList(Math.min(offset, total), endIndex);

            UploadListResponse response = new UploadListResponse();
            response.setUploads(paginatedEntities);
            response.setPagination(new PaginationInfo(total, limit, offset, endIndex < total));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting all BulkUploads", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get statistics about bulk upload operations
     * GET /api/bulkupload/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<UploadStatsResponse> getUploadStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            List<EntityWithMetadata<BulkUpload>> entities = entityService.findAll(modelSpec, BulkUpload.class);

            // In a real implementation, filter by date range if provided
            // For now, calculate stats for all entities

            UploadStatsResponse stats = new UploadStatsResponse();
            stats.setTotalUploads(entities.size());

            long completedUploads = entities.stream()
                    .filter(e -> "completed".equals(e.metadata().getState()))
                    .count();
            stats.setCompletedUploads((int) completedUploads);

            long failedUploads = entities.stream()
                    .filter(e -> "failed".equals(e.metadata().getState()))
                    .count();
            stats.setFailedUploads((int) failedUploads);

            long uploadsWithErrors = entities.stream()
                    .filter(e -> "completed_with_errors".equals(e.metadata().getState()))
                    .count();
            stats.setUploadsWithErrors((int) uploadsWithErrors);

            int totalItemsProcessed = entities.stream()
                    .mapToInt(e -> e.entity().getProcessedItems() != null ? e.entity().getProcessedItems() : 0)
                    .sum();
            stats.setTotalItemsProcessed(totalItemsProcessed);

            int totalItemsFailed = entities.stream()
                    .mapToInt(e -> e.entity().getFailedItems() != null ? e.entity().getFailedItems() : 0)
                    .sum();
            stats.setTotalItemsFailed(totalItemsFailed);

            stats.setAverageProcessingTime("00:02:30"); // Placeholder
            stats.setPeriodStart(LocalDateTime.now().minusDays(30));
            stats.setPeriodEnd(LocalDateTime.now());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting upload stats", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Download detailed error report for a bulk upload
     * GET /api/bulkupload/{id}/errors
     */
    @GetMapping("/{id}/errors")
    public ResponseEntity<ErrorReportResponse> getErrorReport(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(BulkUpload.ENTITY_NAME).withVersion(BulkUpload.ENTITY_VERSION);
            EntityWithMetadata<BulkUpload> upload = entityService.getById(id, modelSpec, BulkUpload.class);

            if (upload == null) {
                return ResponseEntity.notFound().build();
            }

            BulkUpload entity = upload.entity();
            ErrorReportResponse response = new ErrorReportResponse();
            response.setUploadId(entity.getUploadId());
            response.setFileName(entity.getFileName());
            response.setTotalErrors(entity.getErrorMessages() != null ? entity.getErrorMessages().size() : 0);

            // Convert error messages to detailed error objects
            List<ErrorDetail> errors = new ArrayList<>();
            if (entity.getErrorMessages() != null) {
                for (int i = 0; i < entity.getErrorMessages().size(); i++) {
                    ErrorDetail error = new ErrorDetail();
                    error.setItemIndex(i);
                    error.setError(entity.getErrorMessages().get(i));
                    error.setTimestamp(LocalDateTime.now()); // Placeholder
                    errors.add(error);
                }
            }
            response.setErrors(errors);
            response.setGeneratedAt(LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting error report for BulkUpload: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper method for file storage (placeholder)
    private String saveFileToStorage(MultipartFile file, String uploadId) {
        // In a real implementation, this would save the file to a storage system
        // and return the file path or storage key
        return "/uploads/" + uploadId + "/" + file.getOriginalFilename();
    }

    // DTOs for various operations
    @Getter
    @Setter
    public static class UploadListResponse {
        private List<EntityWithMetadata<BulkUpload>> uploads;
        private PaginationInfo pagination;
    }

    @Getter
    @Setter
    public static class PaginationInfo {
        private int total;
        private int limit;
        private int offset;
        private boolean hasMore;

        public PaginationInfo() {}

        public PaginationInfo(int total, int limit, int offset, boolean hasMore) {
            this.total = total;
            this.limit = limit;
            this.offset = offset;
            this.hasMore = hasMore;
        }
    }

    @Getter
    @Setter
    public static class UploadStatsResponse {
        private int totalUploads;
        private int completedUploads;
        private int failedUploads;
        private int uploadsWithErrors;
        private int totalItemsProcessed;
        private int totalItemsFailed;
        private String averageProcessingTime;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }

    @Getter
    @Setter
    public static class ErrorReportResponse {
        private String uploadId;
        private String fileName;
        private int totalErrors;
        private List<ErrorDetail> errors;
        private LocalDateTime generatedAt;
    }

    @Getter
    @Setter
    public static class ErrorDetail {
        private int itemIndex;
        private Long itemId;
        private String error;
        private LocalDateTime timestamp;
    }
}
