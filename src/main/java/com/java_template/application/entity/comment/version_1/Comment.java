package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Comment Entity - Represents individual comments ingested from the JSONPlaceholder API
 * 
 * This entity stores comment data from the JSONPlaceholder API and tracks
 * processing metadata like word count and character count.
 */
@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = Comment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - the "id" field from the API
    private String commentId;
    
    // Required core business fields
    private Integer postId;
    private String name;
    private String email;
    private String body;
    
    // Optional enrichment fields
    private LocalDateTime ingestedAt;
    private Integer wordCount;
    private Integer characterCount;

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
        if (commentId == null || commentId.trim().isEmpty()) {
            return false;
        }
        if (postId == null || postId <= 0) {
            return false;
        }
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        
        // Basic email format validation
        if (!email.contains("@") || !email.contains(".")) {
            return false;
        }
        
        return true;
    }
}
