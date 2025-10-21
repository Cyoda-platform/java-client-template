package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.note.version_1.Note;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Note entity providing CRUD operations
 * and search functionality for the CRM system.
 */
@RestController
@RequestMapping("/ui/note")
@CrossOrigin(origins = "*")
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NoteController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new note
     * POST /ui/note
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Note>> createNote(@Valid @RequestBody Note note) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Note.ENTITY_NAME).withVersion(Note.ENTITY_VERSION);
            EntityWithMetadata<Note> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, note.getNoteId(), "noteId", Note.class);

            if (existing != null) {
                logger.warn("Note with business ID {} already exists", note.getNoteId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Note already exists with ID: %s", note.getNoteId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Note> response = entityService.create(note);
            logger.info("Note created with ID: {}", response.metadata().getId());

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create note", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create note: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get note by technical ID
     * GET /ui/note/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Note>> getNoteById(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Note> note = entityService.findById(id, Note.class);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            logger.error("Failed to get note by ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Note not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get note by business ID
     * GET /ui/note/business/{businessId}
     */
    @GetMapping("/business/{businessId}")
    public ResponseEntity<EntityWithMetadata<Note>> getNoteByBusinessId(@PathVariable String businessId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Note.ENTITY_NAME).withVersion(Note.ENTITY_VERSION);
            EntityWithMetadata<Note> note = entityService.findByBusinessId(
                    modelSpec, businessId, "noteId", Note.class);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            logger.error("Failed to get note by business ID: {}", businessId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Note not found with business ID: %s", businessId)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update note
     * PUT /ui/note/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Note>> updateNote(
            @PathVariable UUID id, 
            @Valid @RequestBody Note note,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Note> response = entityService.update(id, note, transition);
            logger.info("Note updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update note with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update note: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete note
     * DELETE /ui/note/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Note deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete note with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Note not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search notes
     * GET /ui/note/search
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Note>>> searchNotes(
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String title) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Note.ENTITY_NAME).withVersion(Note.ENTITY_VERSION);
            List<QueryCondition> conditions = new ArrayList<>();

            if (companyId != null && !companyId.trim().isEmpty()) {
                SimpleCondition companyCondition = new SimpleCondition()
                        .withJsonPath("$.companyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(companyId));
                conditions.add(companyCondition);
            }

            if (category != null && !category.trim().isEmpty()) {
                SimpleCondition categoryCondition = new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category));
                conditions.add(categoryCondition);
            }

            if (title != null && !title.trim().isEmpty()) {
                SimpleCondition titleCondition = new SimpleCondition()
                        .withJsonPath("$.title")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(title));
                conditions.add(titleCondition);
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Note>> notes = conditions.isEmpty() 
                ? entityService.findAll(modelSpec, Note.class)
                : entityService.search(modelSpec, groupCondition, Note.class);

            return ResponseEntity.ok(notes);
        } catch (Exception e) {
            logger.error("Failed to search notes", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to search notes: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get notes by company ID
     * GET /ui/note/company/{companyId}
     */
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EntityWithMetadata<Note>>> getNotesByCompany(@PathVariable String companyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Note.ENTITY_NAME).withVersion(Note.ENTITY_VERSION);
            
            SimpleCondition companyCondition = new SimpleCondition()
                    .withJsonPath("$.companyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(companyId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(companyCondition));

            List<EntityWithMetadata<Note>> notes = entityService.search(modelSpec, groupCondition, Note.class);
            return ResponseEntity.ok(notes);
        } catch (Exception e) {
            logger.error("Failed to get notes for company: {}", companyId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to get notes for company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Archive note (transition to archived state)
     * POST /ui/note/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<Note>> archiveNote(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Note> note = entityService.findById(id, Note.class);
            EntityWithMetadata<Note> response = entityService.update(id, note.entity(), "archive_note");
            logger.info("Note archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to archive note with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to archive note: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}
