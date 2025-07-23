package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class CommentAnalysisReport implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String ingestionJobId; // reference to CommentIngestionJob (UUID as String)
    private Map<String, Integer> keywordCounts; // count of keywords found
    private Integer totalComments; // number of comments analyzed
    private String sentimentSummary; // optional, summary of sentiment
    private LocalDateTime generatedAt; // timestamp of report generation
    private String status; // StatusEnum (CREATED, SENT)

    public CommentAnalysisReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("commentAnalysisReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "commentAnalysisReport");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && ingestionJobId != null && !ingestionJobId.isBlank()
            && keywordCounts != null
            && totalComments != null;
    }
}
