package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Comment Entity
 * 
 * Represents an individual comment ingested from the JSONPlaceholder API.
 * Contains comment data and analysis results.
 */
@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = Comment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields from API
    private Long commentId;
    private Long postId;
    private String name;
    private String email;
    private String body;
    
    // Processing fields
    private String jobId;
    private LocalDateTime ingestedAt;
    private Integer wordCount;
    private Double sentimentScore;

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
        return commentId != null && commentId > 0 &&
               postId != null && postId > 0 &&
               name != null && !name.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               body != null && !body.trim().isEmpty() &&
               jobId != null && !jobId.trim().isEmpty();
    }
}
