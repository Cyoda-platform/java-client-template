package com.java_template.application.entity.comment_analysis.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CommentAnalysis Entity - Represents the analysis results for a batch of comments from a specific post
 * 
 * This entity stores aggregated analysis data for all comments belonging to a specific postId,
 * including statistical calculations and summary information.
 */
@Data
public class CommentAnalysis implements CyodaEntity {
    public static final String ENTITY_NAME = CommentAnalysis.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - generated UUID as string
    private String analysisId;
    
    // Required core business fields
    private Integer postId;
    private Integer totalComments;
    
    // Optional analysis results
    private Double averageWordCount;
    private Double averageCharacterCount;
    private String mostActiveCommenter;
    private CommentSummary longestComment;
    private CommentSummary shortestComment;
    private Integer uniqueCommenters;
    private LocalDateTime analysisCompletedAt;
    private Boolean emailSent;
    private LocalDateTime emailSentAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        if (analysisId == null || analysisId.trim().isEmpty()) {
            return false;
        }
        if (postId == null || postId <= 0) {
            return false;
        }
        if (totalComments == null || totalComments < 0) {
            return false;
        }
        
        return true;
    }

    /**
     * Nested class for comment summary information
     * Used for storing longest and shortest comment details
     */
    @Data
    public static class CommentSummary {
        private String commentId;
        private String email;
        private Integer wordCount;
        private Integer characterCount;
        private String bodyPreview; // First 100 characters
    }
}
