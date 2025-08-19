package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer postId; // id of the post to ingest comments for
    private String requestedByEmail; // email that requested the report / primary recipient
    private List<String> recipients; // additional email recipients
    private String schedule; // optional cron or run-once flag
    private String status; // PENDING / IN_PROGRESS / COMPLETED / FAILED
    private String createdAt; // timestamp
    private String completedAt; // timestamp
    private String resultReportId; // link to Report.technicalId

    public IngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // postId and requestedByEmail and recipients are required to start a job
        if (this.postId == null) return false;
        if (this.requestedByEmail == null || this.requestedByEmail.isBlank()) return false;
        if (this.recipients == null || this.recipients.isEmpty()) return false;
        return true;
    }
}
