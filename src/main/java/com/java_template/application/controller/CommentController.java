package com.java_template.application.controller;

import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final EntityService entityService;

    public CommentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<?> getAllComments(@RequestParam(required = false) String requestId) {
        try {
            List<EntityResponse<Comment>> responses;
            
            if (requestId != null && !requestId.trim().isEmpty()) {
                logger.info("Getting comments filtered by requestId: {}", requestId);
                
                // Filter by requestId
                Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", requestId);
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                condition.setConditions(List.of(requestIdCondition));
                
                responses = entityService.getItemsByCondition(
                    Comment.class,
                    Comment.ENTITY_NAME,
                    Comment.ENTITY_VERSION,
                    condition,
                    true
                );
            } else {
                logger.info("Getting all comments");
                
                responses = entityService.findAll(
                    Comment.class,
                    Comment.ENTITY_NAME,
                    Comment.ENTITY_VERSION
                );
            }
            
            List<CommentDto> responseDtos = responses.stream()
                .map(this::createResponseDto)
                .toList();
                
            return ResponseEntity.ok(responseDtos);
            
        } catch (Exception e) {
            logger.error("Failed to get comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve comments: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<?> getComment(@PathVariable Long commentId) {
        try {
            logger.info("Getting comment by commentId: {}", commentId);
            
            // Find by business ID (commentId)
            EntityResponse<Comment> response = entityService.findByBusinessId(
                Comment.class,
                Comment.ENTITY_NAME,
                Comment.ENTITY_VERSION,
                commentId.toString(),
                "commentId"
            );
            
            CommentDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get comment by commentId: {}", commentId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "ENTITY_NOT_FOUND", 
                           "message", "Comment with ID " + commentId + " not found",
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping("/by-request/{requestId}")
    public ResponseEntity<?> getCommentsByRequest(@PathVariable String requestId) {
        try {
            logger.info("Getting comments by requestId: {}", requestId);
            
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", requestId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));
            
            List<EntityResponse<Comment>> responses = entityService.getItemsByCondition(
                Comment.class,
                Comment.ENTITY_NAME,
                Comment.ENTITY_VERSION,
                condition,
                true
            );
            
            List<CommentDto> responseDtos = responses.stream()
                .map(this::createResponseDto)
                .toList();
                
            return ResponseEntity.ok(responseDtos);
            
        } catch (Exception e) {
            logger.error("Failed to get comments by requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve comments for request: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    private CommentDto createResponseDto(EntityResponse<Comment> response) {
        Comment entity = response.getData();
        String state = response.getMetadata().getState();
        
        CommentDto dto = new CommentDto();
        dto.setCommentId(entity.getCommentId());
        dto.setPostId(entity.getPostId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setBody(entity.getBody());
        dto.setRequestId(entity.getRequestId());
        dto.setFetchedAt(entity.getFetchedAt());
        dto.setState(state);
        
        return dto;
    }

    // DTO
    public static class CommentDto {
        private Long commentId;
        private Long postId;
        private String name;
        private String email;
        private String body;
        private String requestId;
        private java.time.LocalDateTime fetchedAt;
        private String state;

        // Getters and setters
        public Long getCommentId() { return commentId; }
        public void setCommentId(Long commentId) { this.commentId = commentId; }
        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public java.time.LocalDateTime getFetchedAt() { return fetchedAt; }
        public void setFetchedAt(java.time.LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }
}
