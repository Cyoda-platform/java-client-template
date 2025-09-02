package com.java_template.application.controller;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/comment-analysis-requests")
public class CommentAnalysisRequestController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestController.class);
    private final EntityService entityService;

    public CommentAnalysisRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<?> createCommentAnalysisRequest(@RequestBody CommentAnalysisRequest request) {
        try {
            logger.info("Creating new CommentAnalysisRequest for postId: {}", request.getPostId());
            
            // Save with transition "initialize_request"
            EntityResponse<CommentAnalysisRequest> response = entityService.save(request);
            
            // Create response DTO
            CommentAnalysisRequestDto responseDto = createResponseDto(response);
            
            logger.info("Created CommentAnalysisRequest with requestId: {}", responseDto.getRequestId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to create CommentAnalysisRequest", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "CREATION_FAILED", 
                           "message", "Failed to create CommentAnalysisRequest: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getCommentAnalysisRequest(@PathVariable String requestId) {
        try {
            logger.info("Getting CommentAnalysisRequest by requestId: {}", requestId);
            
            // Find by business ID (requestId)
            EntityResponse<CommentAnalysisRequest> response = entityService.findByBusinessId(
                CommentAnalysisRequest.class,
                CommentAnalysisRequest.ENTITY_NAME,
                CommentAnalysisRequest.ENTITY_VERSION,
                requestId,
                "requestId"
            );
            
            CommentAnalysisRequestDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get CommentAnalysisRequest by requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "ENTITY_NOT_FOUND", 
                           "message", "CommentAnalysisRequest with ID " + requestId + " not found",
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllCommentAnalysisRequests() {
        try {
            logger.info("Getting all CommentAnalysisRequests");
            
            List<EntityResponse<CommentAnalysisRequest>> responses = entityService.findAll(
                CommentAnalysisRequest.class,
                CommentAnalysisRequest.ENTITY_NAME,
                CommentAnalysisRequest.ENTITY_VERSION
            );
            
            List<CommentAnalysisRequestDto> responseDtos = responses.stream()
                .map(this::createResponseDto)
                .toList();
                
            return ResponseEntity.ok(responseDtos);
            
        } catch (Exception e) {
            logger.error("Failed to get all CommentAnalysisRequests", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve CommentAnalysisRequests: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @PutMapping("/{requestId}")
    public ResponseEntity<?> updateCommentAnalysisRequest(
            @PathVariable String requestId, 
            @RequestBody UpdateCommentAnalysisRequestDto updateRequest) {
        try {
            logger.info("Updating CommentAnalysisRequest with requestId: {}", requestId);
            
            // Get existing entity
            EntityResponse<CommentAnalysisRequest> existingResponse = entityService.findByBusinessId(
                CommentAnalysisRequest.class,
                CommentAnalysisRequest.ENTITY_NAME,
                CommentAnalysisRequest.ENTITY_VERSION,
                requestId,
                "requestId"
            );
            
            CommentAnalysisRequest entity = existingResponse.getData();
            UUID entityId = existingResponse.getMetadata().getId();
            
            // Update fields
            if (updateRequest.getRecipientEmail() != null) {
                entity.setRecipientEmail(updateRequest.getRecipientEmail());
            }
            
            // Update with optional transition
            EntityResponse<CommentAnalysisRequest> response = entityService.update(
                entityId, entity, updateRequest.getTransitionName()
            );
            
            CommentAnalysisRequestDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to update CommentAnalysisRequest with requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "UPDATE_FAILED", 
                           "message", "Failed to update CommentAnalysisRequest: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @PostMapping("/{requestId}/retry")
    public ResponseEntity<?> retryCommentAnalysisRequest(@PathVariable String requestId) {
        try {
            logger.info("Retrying CommentAnalysisRequest with requestId: {}", requestId);
            
            // Get existing entity
            EntityResponse<CommentAnalysisRequest> existingResponse = entityService.findByBusinessId(
                CommentAnalysisRequest.class,
                CommentAnalysisRequest.ENTITY_NAME,
                CommentAnalysisRequest.ENTITY_VERSION,
                requestId,
                "requestId"
            );
            
            CommentAnalysisRequest entity = existingResponse.getData();
            UUID entityId = existingResponse.getMetadata().getId();
            
            // Reset failure fields
            entity.setFailedAt(null);
            entity.setFailureReason(null);
            
            // Update with "initialize_request" transition to retry
            EntityResponse<CommentAnalysisRequest> response = entityService.update(
                entityId, entity, "initialize_request"
            );
            
            CommentAnalysisRequestDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to retry CommentAnalysisRequest with requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "RETRY_FAILED", 
                           "message", "Failed to retry CommentAnalysisRequest: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    private CommentAnalysisRequestDto createResponseDto(EntityResponse<CommentAnalysisRequest> response) {
        CommentAnalysisRequest entity = response.getData();
        String state = response.getMetadata().getState();
        
        CommentAnalysisRequestDto dto = new CommentAnalysisRequestDto();
        dto.setRequestId(entity.getRequestId());
        dto.setPostId(entity.getPostId());
        dto.setRecipientEmail(entity.getRecipientEmail());
        dto.setRequestedAt(entity.getRequestedAt());
        dto.setState(state);
        
        return dto;
    }

    // DTOs
    public static class CommentAnalysisRequestDto {
        private String requestId;
        private Long postId;
        private String recipientEmail;
        private java.time.LocalDateTime requestedAt;
        private String state;

        // Getters and setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public String getRecipientEmail() { return recipientEmail; }
        public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
        public java.time.LocalDateTime getRequestedAt() { return requestedAt; }
        public void setRequestedAt(java.time.LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    public static class UpdateCommentAnalysisRequestDto {
        private String recipientEmail;
        private String transitionName;

        // Getters and setters
        public String getRecipientEmail() { return recipientEmail; }
        public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
        public String getTransitionName() { return transitionName; }
        public void setTransitionName(String transitionName) { this.transitionName = transitionName; }
    }
}
