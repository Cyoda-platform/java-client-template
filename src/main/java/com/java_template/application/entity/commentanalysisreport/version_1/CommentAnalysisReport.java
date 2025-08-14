package com.java_template.application.entity.commentanalysisreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class CommentAnalysisReport implements CyodaEntity {
    public static final String ENTITY_NAME = "CommentAnalysisReport";
    public static final Integer ENTITY_VERSION = 1;

    private Long postId;
    private String sentimentSummary;
    private String htmlReport;
    private String createdAt;

    public CommentAnalysisReport() {}

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
        if(sentimentSummary == null || sentimentSummary.isBlank()) return false;
        if(htmlReport == null || htmlReport.isBlank()) return false;
        if(createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}}