package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Report implements CyodaEntity {

    private int postId;
    private String summary;
    private AnalysisDetails analysisDetails;
    private String generatedAt;

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("report");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "report");
    }

    @Override
    public boolean isValid() {
        return postId > 0 && summary != null && !summary.isEmpty() && analysisDetails != null && analysisDetails.isValid() && generatedAt != null && !generatedAt.isEmpty();
    }

    @Data
    public static class AnalysisDetails {
        private double sentimentScore;
        private java.util.Map<String, Integer> wordFrequency;

        public AnalysisDetails() {}

        public boolean isValid() {
            return wordFrequency != null;
        }
    }
