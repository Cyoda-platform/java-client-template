package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.PageResponse;
import com.java_template.application.entity.comment.version_1.Comment;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CommentController
 * 
 * REST controller for managing Comment entities.
 * Provides read operations and workflow transitions.
 */
@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "*")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get comment by technical UUID
     * GET /api/comments/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Comment>> getCommentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Comment.ENTITY_NAME).withVersion(Comment.ENTITY_VERSION);
            EntityWithMetadata<Comment> response = entityService.getById(id, modelSpec, Comment.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Comment by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all comments with filtering options
     * GET /api/comments
     */
    @GetMapping
    public ResponseEntity<PageResponse<EntityWithMetadata<Comment>>> getAllComments(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Comment.ENTITY_NAME).withVersion(Comment.ENTITY_VERSION);
            
            List<EntityWithMetadata<Comment>> comments;
            
            if (jobId != null || postId != null) {
                // Build search condition
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (jobId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.jobId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(jobId)));
                }
                
                if (postId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.postId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(postId)));
                }
                
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                
                comments = entityService.search(modelSpec, condition, Comment.class);
            } else {
                comments = entityService.findAll(modelSpec, Comment.class);
            }
            
            // Filter by state if provided (since state is in metadata, not entity)
            if (state != null) {
                comments = comments.stream()
                        .filter(comment -> state.equals(comment.metadata().getState()))
                        .toList();
            }
            
            // Manual pagination
            int start = page * size;
            int end = Math.min(start + size, comments.size());

            List<EntityWithMetadata<Comment>> pageContent = comments.subList(start, end);
            PageResponse<EntityWithMetadata<Comment>> pageResult = new PageResponse<>(pageContent, page, size, comments.size());

            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            logger.error("Error getting Comments", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update comment (mainly for reprocessing)
     * PUT /api/comments/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Comment>> updateComment(
            @PathVariable UUID id,
            @RequestBody UpdateCommentRequest request) {
        try {
            // Get existing comment first
            ModelSpec modelSpec = new ModelSpec().withName(Comment.ENTITY_NAME).withVersion(Comment.ENTITY_VERSION);
            EntityWithMetadata<Comment> existing = entityService.getById(id, modelSpec, Comment.class);
            
            Comment comment = existing.entity();

            // Update entity with optional transition
            EntityWithMetadata<Comment> response = entityService.update(id, comment, request.getTransitionName());
            logger.info("Comment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Comment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for update requests
     */
    @Getter
    @Setter
    public static class UpdateCommentRequest {
        private String transitionName;
    }
}
