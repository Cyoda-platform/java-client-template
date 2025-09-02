package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = Comment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long commentId;
    private Long postId;
    private String name;
    private String email;
    private String body;
    private String requestId;
    private LocalDateTime fetchedAt;
    private LocalDateTime processedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return commentId != null && commentId > 0 &&
               postId != null && postId > 0 &&
               name != null && !name.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               body != null && !body.trim().isEmpty() &&
               requestId != null && !requestId.trim().isEmpty();
    }
}
