package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = "Comment";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer commentId; // source comment id
    private Integer postId; // links to post
    private String authorName;
    private String authorEmail;
    private String body;
    private String receivedAt; // timestamp
    private Double sentimentScore; // analysis result
    private List<String> keywords;
    private List<String> flags; // e.g., TOXIC, DUPLICATE

    public Comment() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // commentId and postId and body should be present
        if (this.commentId == null) return false;
        if (this.postId == null) return false;
        if (this.body == null || this.body.isBlank()) return false;
        return true;
    }
}
