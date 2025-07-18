package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Comment implements CyodaEntity {
    private String commentId;
    private String author;
    private String text;
    private Instant createdAt;

    public Comment() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("comment");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "comment");
    }

    @Override
    public boolean isValid() {
        return commentId != null && !commentId.isBlank() && author != null && !author.isBlank() && text != null && !text.isBlank() && createdAt != null;
    }
}
