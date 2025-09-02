package com.java_template.application.entity.commentanalysis.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class CommentAnalysis implements CyodaEntity {
    public static final String ENTITY_NAME = CommentAnalysis.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String requestId;
    private Integer totalComments;
    private Double averageCommentLength;
    private Integer uniqueAuthors;
    private String topKeywords;
    private String sentimentSummary;
    private LocalDateTime analysisCompletedAt;
    private String analysisId;
    private LocalDateTime analysisStartedAt;
    private LocalDateTime analysisFailedAt;
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
        return requestId != null && !requestId.trim().isEmpty() &&
               analysisId != null && !analysisId.trim().isEmpty();
    }
}
