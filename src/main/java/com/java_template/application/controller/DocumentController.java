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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Document management
 * Handles digital document management with versioning and audit trail
 */
@RestController
@RequestMapping("/ui/documents")
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
     * Create a new document
     * POST /ui/documents
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Document>> createDocument(@RequestBody Document document) {
        try {
            // Set creation timestamp and initial status
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            document.setUploadedAt(LocalDateTime.now());
            
            if (document.getStatus() == null) {
                document.setStatus("draft");
            }
            
            if (document.getVersionNumber() == null) {
                document.setVersionNumber(1);
            }

            EntityWithMetadata<Document> response = entityService.create(document);
            logger.info("Document created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get document by technical UUID
     * GET /ui/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Document>> getDocumentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> response = entityService.getById(id, modelSpec, Document.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting document by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get document by business identifier
     * GET /ui/documents/business/{documentId}
     */
    @GetMapping("/business/{documentId}")
    public ResponseEntity<EntityWithMetadata<Document>> getDocumentByBusinessId(@PathVariable String documentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> response = entityService.findByBusinessId(
                    modelSpec, documentId, "documentId", Document.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting document by business ID: {}", documentId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update document with optional workflow transition
     * PUT /ui/documents/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Document>> updateDocument(
            @PathVariable UUID id,
            @RequestBody Document document,
            @RequestParam(required = false) String transition) {
        try {
            document.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Document> response = entityService.update(id, document, transition);
            logger.info("Document updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete document by technical UUID
     * DELETE /ui/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Document deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all documents
     * GET /ui/documents
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Document>>> getAllDocuments() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            List<EntityWithMetadata<Document>> documents = entityService.findAll(modelSpec, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error getting all documents", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search documents by type
     * GET /ui/documents/search?type=protocol
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Document>>> searchDocumentsByType(
            @RequestParam String type) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.type")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(type));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Document>> documents = entityService.search(modelSpec, condition, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error searching documents by type: {}", type, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for documents
     * POST /ui/documents/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Document>>> advancedSearch(
            @RequestBody DocumentSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getType() != null && !searchRequest.getType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.type")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getType())));
            }

            if (searchRequest.getStatus() != null && !searchRequest.getStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStatus())));
            }

            if (searchRequest.getSubmissionId() != null && !searchRequest.getSubmissionId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.submissionId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSubmissionId())));
            }

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Document>> documents = entityService.search(modelSpec, condition, Document.class);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Finalize document (workflow transition)
     * POST /ui/documents/{id}/finalize
     */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<EntityWithMetadata<Document>> finalizeDocument(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> current = entityService.getById(id, modelSpec, Document.class);
            
            Document document = current.entity();
            document.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Document> response = entityService.update(id, document, "finalize_document");
            logger.info("Document {} finalized", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error finalizing document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new version of document (workflow transition)
     * POST /ui/documents/{id}/new-version
     */
    @PostMapping("/{id}/new-version")
    public ResponseEntity<EntityWithMetadata<Document>> createNewVersion(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Document.ENTITY_NAME).withVersion(Document.ENTITY_VERSION);
            EntityWithMetadata<Document> current = entityService.getById(id, modelSpec, Document.class);
            
            Document document = current.entity();
            document.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Document> response = entityService.update(id, document, "create_new_version");
            logger.info("New version created for document {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating new version for document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class DocumentSearchRequest {
        private String type;
        private String status;
        private String submissionId;
        private String name;
    }
}
