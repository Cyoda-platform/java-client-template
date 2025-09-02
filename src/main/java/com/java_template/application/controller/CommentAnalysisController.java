package com.java_template.application.controller;

import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
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
@RequestMapping("/api/comment-analyses")
public class CommentAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisController.class);
    private final EntityService entityService;

    public CommentAnalysisController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<?> getCommentAnalysis(@PathVariable String analysisId) {
        try {
            logger.info("Getting CommentAnalysis by analysisId: {}", analysisId);
            
            // Find by business ID (analysisId)
            EntityResponse<CommentAnalysis> response = entityService.findByBusinessId(
                CommentAnalysis.class,
                CommentAnalysis.ENTITY_NAME,
                CommentAnalysis.ENTITY_VERSION,
                analysisId,
                "analysisId"
            );
            
            CommentAnalysisDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get CommentAnalysis by analysisId: {}", analysisId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "ENTITY_NOT_FOUND", 
                           "message", "CommentAnalysis with ID " + analysisId + " not found",
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping("/by-request/{requestId}")
    public ResponseEntity<?> getCommentAnalysisByRequest(@PathVariable String requestId) {
        try {
            logger.info("Getting CommentAnalysis by requestId: {}", requestId);
            
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", requestId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));
            
            Optional<EntityResponse<CommentAnalysis>> analysisResponse = 
                entityService.getFirstItemByCondition(
                    CommentAnalysis.class,
                    CommentAnalysis.ENTITY_NAME,
                    CommentAnalysis.ENTITY_VERSION,
                    condition,
                    true
                );
            
            if (!analysisResponse.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "ENTITY_NOT_FOUND", 
                               "message", "CommentAnalysis for request " + requestId + " not found",
                               "timestamp", java.time.LocalDateTime.now()));
            }
            
            CommentAnalysisDto responseDto = createResponseDto(analysisResponse.get());
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get CommentAnalysis by requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve CommentAnalysis for request: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllCommentAnalyses() {
        try {
            logger.info("Getting all CommentAnalyses");
            
            List<EntityResponse<CommentAnalysis>> responses = entityService.findAll(
                CommentAnalysis.class,
                CommentAnalysis.ENTITY_NAME,
                CommentAnalysis.ENTITY_VERSION
            );
            
            List<CommentAnalysisDto> responseDtos = responses.stream()
                .map(this::createResponseDto)
                .toList();
                
            return ResponseEntity.ok(responseDtos);
            
        } catch (Exception e) {
            logger.error("Failed to get all CommentAnalyses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve CommentAnalyses: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    private CommentAnalysisDto createResponseDto(EntityResponse<CommentAnalysis> response) {
        CommentAnalysis entity = response.getData();
        String state = response.getMetadata().getState();
        
        CommentAnalysisDto dto = new CommentAnalysisDto();
        dto.setAnalysisId(entity.getAnalysisId());
        dto.setRequestId(entity.getRequestId());
        dto.setTotalComments(entity.getTotalComments());
        dto.setAverageCommentLength(entity.getAverageCommentLength());
        dto.setUniqueAuthors(entity.getUniqueAuthors());
        dto.setTopKeywords(entity.getTopKeywords());
        dto.setSentimentSummary(entity.getSentimentSummary());
        dto.setAnalysisCompletedAt(entity.getAnalysisCompletedAt());
        dto.setState(state);
        
        return dto;
    }

    // DTO
    public static class CommentAnalysisDto {
        private String analysisId;
        private String requestId;
        private Integer totalComments;
        private Double averageCommentLength;
        private Integer uniqueAuthors;
        private String topKeywords;
        private String sentimentSummary;
        private java.time.LocalDateTime analysisCompletedAt;
        private String state;

        // Getters and setters
        public String getAnalysisId() { return analysisId; }
        public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Integer getTotalComments() { return totalComments; }
        public void setTotalComments(Integer totalComments) { this.totalComments = totalComments; }
        public Double getAverageCommentLength() { return averageCommentLength; }
        public void setAverageCommentLength(Double averageCommentLength) { this.averageCommentLength = averageCommentLength; }
        public Integer getUniqueAuthors() { return uniqueAuthors; }
        public void setUniqueAuthors(Integer uniqueAuthors) { this.uniqueAuthors = uniqueAuthors; }
        public String getTopKeywords() { return topKeywords; }
        public void setTopKeywords(String topKeywords) { this.topKeywords = topKeywords; }
        public String getSentimentSummary() { return sentimentSummary; }
        public void setSentimentSummary(String sentimentSummary) { this.sentimentSummary = sentimentSummary; }
        public java.time.LocalDateTime getAnalysisCompletedAt() { return analysisCompletedAt; }
        public void setAnalysisCompletedAt(java.time.LocalDateTime analysisCompletedAt) { this.analysisCompletedAt = analysisCompletedAt; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }
}
