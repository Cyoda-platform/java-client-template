package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = "Comment";
    public static final Integer ENTITY_VERSION = 1;

    private Long postId;
    private Long commentId;
    private String name;
    private String email;
    private String body;

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
        if(postId == null || postId <= 0) return false;
        if(commentId == null || commentId <= 0) return false;
        if(name == null || name.isBlank()) return false;
        if(email == null || email.isBlank()) return false;
        if(body == null || body.isBlank()) return false;
        return true;
    }
}
