package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CommentController - Manage individual comments from JSONPlaceholder API
 * 
 * Base Path: /api/comments
 * Purpose: Manage individual comments from JSONPlaceholder API
 */
@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "*")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public CommentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Ingest comments for a specific post from JSONPlaceholder API
     * POST /api/comments/ingest/{postId}
     */
    @PostMapping("/ingest/{postId}")
    public ResponseEntity<IngestResponse> ingestComments(@PathVariable Integer postId, @RequestBody(required = false) IngestRequest request) {
        try {
            logger.info("Ingesting comments for postId: {}", postId);

            // Determine API URL
            String apiUrl = (request != null && request.getApiUrl() != null) 
                    ? request.getApiUrl() 
                    : "https://jsonplaceholder.typicode.com/posts/" + postId + "/comments";

            // Fetch comments from JSONPlaceholder API
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apiComments = restTemplate.getForObject(apiUrl, List.class);

            if (apiComments == null || apiComments.isEmpty()) {
                logger.warn("No comments found for postId: {}", postId);
                return ResponseEntity.ok(new IngestResponse(false, "No comments found for post " + postId, new ArrayList<>()));
            }

            List<EntityWithMetadata<Comment>> ingestedComments = new ArrayList<>();

            // Process each comment from API
            for (Map<String, Object> apiComment : apiComments) {
                Comment comment = new Comment();
                comment.setCommentId(String.valueOf(apiComment.get("id")));
                comment.setPostId(((Number) apiComment.get("postId")).intValue());
                comment.setName((String) apiComment.get("name"));
                comment.setEmail((String) apiComment.get("email"));
                comment.setBody((String) apiComment.get("body"));

                // Create comment entity (triggers workflow: none → ingested)
                EntityWithMetadata<Comment> createdComment = entityService.create(comment);
                ingestedComments.add(createdComment);
                
                logger.debug("Created comment: {} for postId: {}", comment.getCommentId(), postId);
            }

            logger.info("Successfully ingested {} comments for postId: {}", ingestedComments.size(), postId);

            IngestResponse response = new IngestResponse(
                true, 
                "Successfully ingested " + ingestedComments.size() + " comments for post " + postId,
                ingestedComments
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error ingesting comments for postId: {}", postId, e);
            return ResponseEntity.badRequest().body(
                new IngestResponse(false, "Error ingesting comments: " + e.getMessage(), new ArrayList<>())
            );
        }
    }

    /**
     * Get comment by technical UUID
     * GET /api/comments/{id}
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
     * Get all comments for a specific post
     * GET /api/comments/post/{postId}
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<EntityWithMetadata<Comment>>> getCommentsByPostId(@PathVariable Integer postId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Comment.ENTITY_NAME).withVersion(Comment.ENTITY_VERSION);

            SimpleCondition postIdCondition = new SimpleCondition()
                    .withJsonPath("$.postId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(postId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(postIdCondition));

            List<EntityWithMetadata<Comment>> comments = entityService.search(modelSpec, condition, Comment.class);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            logger.error("Error getting Comments by postId: {}", postId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update comment with optional transition
     * PUT /api/comments/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Comment>> updateComment(
            @PathVariable UUID id,
            @RequestBody Comment comment,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Comment> response = entityService.update(id, comment, transition);
            logger.info("Comment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Comment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete comment by technical UUID
     * DELETE /api/comments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Comment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Comment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class IngestRequest {
        private String apiUrl;
    }

    @Getter
    @Setter
    public static class IngestResponse {
        private boolean success;
        private String message;
        private List<EntityWithMetadata<Comment>> ingestedComments;

        public IngestResponse() {}

        public IngestResponse(boolean success, String message, List<EntityWithMetadata<Comment>> ingestedComments) {
            this.success = success;
            this.message = message;
            this.ingestedComments = ingestedComments;
        }
    }
}
