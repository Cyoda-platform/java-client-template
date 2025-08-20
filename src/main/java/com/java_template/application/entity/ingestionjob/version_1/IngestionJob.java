package com.java_template.application.entity.ingestionjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobId; // business id for this ingestion request
    private String runDate; // ISO date the job should ingest for
    private String timezone; // timezone context for runDate
    private String source; // data source identifier, e.g., Fakerest
    private String status; // PENDING / RUNNING / COMPLETED / FAILED
    private String startedAt; // timestamp
    private String finishedAt; // timestamp
    private JsonNode summary; // high level stats after run (JsonNode to allow object node operations)
    private String initiatedBy; // system or user
    private String failureReason; // human readable failure

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
        if (jobId == null || jobId.isBlank()) return false;
        if (runDate == null || runDate.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        return true;
    }
}
