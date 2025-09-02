package com.java_template.application.entity.commentanalysisrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class CommentAnalysisRequest implements CyodaEntity {
    public static final String ENTITY_NAME = CommentAnalysisRequest.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long postId;
    private String recipientEmail;
    private LocalDateTime requestedAt;
    private String requestId;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String failureReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return postId != null && postId > 0 && 
               recipientEmail != null && !recipientEmail.trim().isEmpty() &&
               recipientEmail.contains("@");
    }
}
