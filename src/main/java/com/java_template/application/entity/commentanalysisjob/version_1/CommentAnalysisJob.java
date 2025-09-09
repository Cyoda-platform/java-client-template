package com.java_template.application.entity.commentanalysisjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CommentAnalysisJob Entity
 * 
 * Represents a job to analyze comments for a specific post ID.
 * This entity manages the lifecycle of comment analysis from ingestion to report delivery.
 */
@Data
public class CommentAnalysisJob implements CyodaEntity {
    public static final String ENTITY_NAME = CommentAnalysisJob.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business fields
    private Long postId;
    private String recipientEmail;
    private LocalDateTime requestedAt;
    
    // Optional fields set during processing
    private LocalDateTime completedAt;
    private Integer totalComments;
    private String errorMessage;

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
        return postId != null && postId > 0 && 
               recipientEmail != null && !recipientEmail.trim().isEmpty() &&
               recipientEmail.contains("@") && recipientEmail.contains(".");
    }
}
