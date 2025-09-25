package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.document.version_1.Document;
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
 * DocumentController - REST controller for Document entity operations
 * Handles document upload, validation, and management
 */
@RestController
@RequestMapping("/ui/document")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DocumentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload a new document
     * POST /ui/document
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Document>> uploadDocument(@RequestBody Document document) {
        try {
            // Set upload timestamp
            document.setUploadDate(LocalDateTime.now());
            
            // Set default version if not provided
            if (document.getVersion() == null) {
                document.setVersion(1);
            }

            EntityWithMetadata<Document> response = entityService.create(document);
            logger.info("Document uploaded with ID: {} and filename: {}", response.metadata().getId(), document.getFileName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get document by technical UUID
     * GET /ui/document/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Document>> getDocumentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> response = entityService.getById(id, modelSpec, Document.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Document by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update document with optional workflow transition
     * PUT /ui/document/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Document>> updateDocument(
            @PathVariable UUID id,
            @RequestBody Document document,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Document> response = entityService.update(id, document, transition);
            logger.info("Document updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate document
     * PUT /ui/document/{id}/validate
     */
    @PutMapping("/{id}/validate")
    public ResponseEntity<EntityWithMetadata<Document>> validateDocument(@PathVariable UUID id) {
        try {
            // Get current document
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> currentDocument = entityService.getById(id, modelSpec, Document.class);
            
            Document document = currentDocument.entity();
            
            EntityWithMetadata<Document> response = entityService.update(id, document, "validate_document");
            logger.info("Document validated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error validating Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Archive document
     * PUT /ui/document/{id}/archive
     */
    @PutMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<Document>> archiveDocument(@PathVariable UUID id) {
        try {
            // Get current document
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> currentDocument = entityService.getById(id, modelSpec, Document.class);
            
            Document document = currentDocument.entity();
            
            EntityWithMetadata<Document> response = entityService.update(id, document, "archive_document");
            logger.info("Document archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error archiving Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete document
     * PUT /ui/document/{id}/delete
     */
    @PutMapping("/{id}/delete")
    public ResponseEntity<EntityWithMetadata<Document>> deleteDocument(@PathVariable UUID id) {
        try {
            // Get current document
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> currentDocument = entityService.getById(id, modelSpec, Document.class);
            
            Document document = currentDocument.entity();
            
            EntityWithMetadata<Document> response = entityService.update(id, document, "delete_document");
            logger.info("Document deleted with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete document by technical UUID (hard delete)
     * DELETE /ui/document/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDeleteDocument(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Document hard deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error hard deleting Document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all documents
     * GET /ui/document
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Document>>> getAllDocuments() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            List<EntityWithMetadata<Document>> documents = entityService.findAll(modelSpec, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error getting all Documents", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get documents by submission ID
     * GET /ui/document/submission/{submissionId}
     */
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<List<EntityWithMetadata<Document>>> getDocumentsBySubmission(@PathVariable String submissionId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.submissionId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(submissionId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Document>> documents = entityService.search(modelSpec, condition, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error getting Documents by submission ID: {}", submissionId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get documents by uploader
     * GET /ui/document/uploader/{uploaderEmail}
     */
    @GetMapping("/uploader/{uploaderEmail}")
    public ResponseEntity<List<EntityWithMetadata<Document>>> getDocumentsByUploader(@PathVariable String uploaderEmail) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.uploadedBy")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(uploaderEmail));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Document>> documents = entityService.search(modelSpec, condition, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error getting Documents by uploader: {}", uploaderEmail, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search documents by file type
     * GET /ui/document/search/type?fileType=TYPE
     */
    @GetMapping("/search/type")
    public ResponseEntity<List<EntityWithMetadata<Document>>> searchDocumentsByType(@RequestParam String fileType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.fileType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(fileType));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Document>> documents = entityService.search(modelSpec, condition, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error searching Documents by file type: {}", fileType, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
