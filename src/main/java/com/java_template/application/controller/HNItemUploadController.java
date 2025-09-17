package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitemupload.version_1.HNItemUpload;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HNItemUploadController - REST controller for managing HN item uploads
 */
@RestController
@RequestMapping("/api/hnitem/upload")
@CrossOrigin(origins = "*")
public class HNItemUploadController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemUploadController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HNItemUploadController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload a single HN item
     * POST /api/hnitem/upload/single
     */
    @PostMapping("/single")
    public ResponseEntity<EntityWithMetadata<HNItemUpload>> uploadSingleItem(@RequestBody SingleUploadRequest request) {
        try {
            logger.info("Creating single item upload");
            
            // Create HNItemUpload entity
            HNItemUpload uploadEntity = new HNItemUpload();
            uploadEntity.setUploadId(generateUploadId());
            uploadEntity.setUploadType("single");
            uploadEntity.setUploadTimestamp(LocalDateTime.now());

            // Validate the upload entity
            if (!uploadEntity.isValid()) {
                logger.error("Invalid single upload request");
                return ResponseEntity.badRequest().build();
            }

            // Create the entity - transition will be auto_create (automatic)
            EntityWithMetadata<HNItemUpload> response = entityService.create(uploadEntity);
            logger.info("Single upload created with technical ID: {} and uploadId: {}", 
                       response.metadata().getId(), uploadEntity.getUploadId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating single upload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload array of HN items
     * POST /api/hnitem/upload/array
     */
    @PostMapping("/array")
    public ResponseEntity<EntityWithMetadata<HNItemUpload>> uploadArrayItems(@RequestBody ArrayUploadRequest request) {
        try {
            logger.info("Creating array upload with {} items", request.getItems().size());
            
            // Create HNItemUpload entity
            HNItemUpload uploadEntity = new HNItemUpload();
            uploadEntity.setUploadId(generateUploadId());
            uploadEntity.setUploadType("array");
            uploadEntity.setUploadTimestamp(LocalDateTime.now());

            // Validate the upload entity
            if (!uploadEntity.isValid()) {
                logger.error("Invalid array upload request");
                return ResponseEntity.badRequest().build();
            }

            // Create the entity
            EntityWithMetadata<HNItemUpload> response = entityService.create(uploadEntity);
            logger.info("Array upload created with technical ID: {} and uploadId: {}", 
                       response.metadata().getId(), uploadEntity.getUploadId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating array upload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload HN items from JSON file
     * POST /api/hnitem/upload/file
     */
    @PostMapping("/file")
    public ResponseEntity<EntityWithMetadata<HNItemUpload>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            logger.info("Creating file upload for file: {}", file.getOriginalFilename());
            
            // Validate file
            if (file.isEmpty()) {
                logger.error("Uploaded file is empty");
                return ResponseEntity.badRequest().build();
            }

            // Create HNItemUpload entity
            HNItemUpload uploadEntity = new HNItemUpload();
            uploadEntity.setUploadId(generateUploadId());
            uploadEntity.setUploadType("file");
            uploadEntity.setFileName(file.getOriginalFilename());
            uploadEntity.setUploadTimestamp(LocalDateTime.now());

            // Validate the upload entity
            if (!uploadEntity.isValid()) {
                logger.error("Invalid file upload request");
                return ResponseEntity.badRequest().build();
            }

            // Create the entity
            EntityWithMetadata<HNItemUpload> response = entityService.create(uploadEntity);
            logger.info("File upload created with technical ID: {} and uploadId: {}", 
                       response.metadata().getId(), uploadEntity.getUploadId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating file upload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get upload by technical UUID
     * GET /api/hnitem/upload/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HNItemUpload>> getUploadById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemUpload.ENTITY_NAME).withVersion(HNItemUpload.ENTITY_VERSION);
            EntityWithMetadata<HNItemUpload> response = entityService.getById(id, modelSpec, HNItemUpload.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting upload by technical ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get upload by business ID (uploadId)
     * GET /api/hnitem/upload/business/{uploadId}
     */
    @GetMapping("/business/{uploadId}")
    public ResponseEntity<EntityWithMetadata<HNItemUpload>> getUploadByBusinessId(@PathVariable String uploadId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemUpload.ENTITY_NAME).withVersion(HNItemUpload.ENTITY_VERSION);
            EntityWithMetadata<HNItemUpload> response = entityService.findByBusinessId(
                    modelSpec, uploadId, "uploadId", HNItemUpload.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting upload by business ID: {}", uploadId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all uploads (USE SPARINGLY)
     * GET /api/hnitem/upload
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<HNItemUpload>>> getAllUploads() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemUpload.ENTITY_NAME).withVersion(HNItemUpload.ENTITY_VERSION);
            List<EntityWithMetadata<HNItemUpload>> uploads = entityService.findAll(modelSpec, HNItemUpload.class);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            logger.error("Error getting all uploads", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get uploads by type
     * GET /api/hnitem/upload/type?uploadType=single
     */
    @GetMapping("/type")
    public ResponseEntity<List<EntityWithMetadata<HNItemUpload>>> getUploadsByType(@RequestParam String uploadType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItemUpload.ENTITY_NAME).withVersion(HNItemUpload.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.uploadType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(uploadType));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItemUpload>> uploads = entityService.search(modelSpec, condition, HNItemUpload.class);
            return ResponseEntity.ok(uploads);
        } catch (Exception e) {
            logger.error("Error searching uploads by type: {}", uploadType, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get upload status summary
     * GET /api/hnitem/upload/{uploadId}/status
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<UploadStatusResponse> getUploadStatus(@PathVariable String uploadId) {
        try {
            // Find the upload entity
            ModelSpec modelSpec = new ModelSpec().withName(HNItemUpload.ENTITY_NAME).withVersion(HNItemUpload.ENTITY_VERSION);
            EntityWithMetadata<HNItemUpload> uploadResponse = entityService.findByBusinessId(
                    modelSpec, uploadId, "uploadId", HNItemUpload.class);

            if (uploadResponse == null) {
                return ResponseEntity.notFound().build();
            }

            HNItemUpload uploadEntity = uploadResponse.entity();
            
            UploadStatusResponse response = new UploadStatusResponse();
            response.setUploadId(uploadId);
            response.setUploadType(uploadEntity.getUploadType());
            response.setFileName(uploadEntity.getFileName());
            response.setTotalItems(uploadEntity.getTotalItems() != null ? uploadEntity.getTotalItems() : 0);
            response.setProcessedItems(uploadEntity.getProcessedItems() != null ? uploadEntity.getProcessedItems() : 0);
            response.setFailedItems(uploadEntity.getFailedItems() != null ? uploadEntity.getFailedItems() : 0);
            response.setErrorMessages(uploadEntity.getErrorMessages());
            response.setUploadTimestamp(uploadEntity.getUploadTimestamp());
            response.setCompletionTimestamp(uploadEntity.getCompletionTimestamp());
            response.setState(uploadResponse.metadata().getState());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting upload status for uploadId: {}", uploadId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a unique upload ID
     */
    private String generateUploadId() {
        return "upload-" + System.currentTimeMillis();
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class SingleUploadRequest {
        private String uploadType;
        private Map<String, Object> item;
    }

    @Getter
    @Setter
    public static class ArrayUploadRequest {
        private String uploadType;
        private List<Map<String, Object>> items;
    }

    @Getter
    @Setter
    public static class UploadStatusResponse {
        private String uploadId;
        private String uploadType;
        private String fileName;
        private Integer totalItems;
        private Integer processedItems;
        private Integer failedItems;
        private List<String> errorMessages;
        private LocalDateTime uploadTimestamp;
        private LocalDateTime completionTimestamp;
        private String state;
    }
}
