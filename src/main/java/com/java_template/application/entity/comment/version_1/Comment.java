package com.java_template.application.entity.comment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Comment implements CyodaEntity {
    public static final String ENTITY_NAME = "Comment"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Integer id; // technical id
    private String body;
    private String email;
    private String fetchedAt; // ISO timestamp as String
    private String name;
    private String postId; // foreign key reference (serialized UUID as String)
    private String source;

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
        // Validate required string fields using isBlank()
        if (body == null || body.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (fetchedAt == null || fetchedAt.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (postId == null || postId.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        return true;
    }
}