package com.java_template.application.entity.commentanalysisreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CommentAnalysisReport Entity
 * 
 * Represents the final analysis report containing aggregated insights about comments.
 * Contains statistical analysis and summary data for email delivery.
 */
@Data
public class CommentAnalysisReport implements CyodaEntity {
    public static final String ENTITY_NAME = CommentAnalysisReport.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String jobId;
    private Long postId;
    private Integer totalComments;
    
    // Analysis metrics
    private Double averageWordCount;
    private Double averageSentimentScore;
    private Integer positiveCommentsCount;
    private Integer negativeCommentsCount;
    private Integer neutralCommentsCount;
    
    // Additional insights
    private String topCommenters;
    private String commonKeywords;
    
    // Timestamps
    private LocalDateTime generatedAt;
    private LocalDateTime sentAt;

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
        return jobId != null && !jobId.trim().isEmpty() &&
               postId != null && postId > 0 &&
               totalComments != null && totalComments >= 0;
    }
}
