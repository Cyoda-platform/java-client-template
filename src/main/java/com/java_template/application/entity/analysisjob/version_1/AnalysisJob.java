package com.java_template.application.entity.analysisjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;
import java.util.HashMap;

@Data
public class AnalysisJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AnalysisJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // internal id for the orchestration job
    private String jobName; // human readable
    private String csvfileId; // linked CSVFile id (UUID serialized as String)
    private String analysisType; // summary | timeseries | anomaly | custom
    private Map<String, String> parameters = new HashMap<>(); // group_by, metrics, date_range, thresholds
    private String schedule; // on_upload | manual | cron expression
    private String status; // PENDING | VALIDATING | QUEUED | RUNNING | COMPLETED | FAILED
    private String startedAt; // datetime (ISO string)
    private String completedAt; // datetime (ISO string)
    private String reportLocation; // path/uri where report is stored
    private String reportSummary; // short text summary

    public AnalysisJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobName == null || jobName.isBlank()) return false;
        if (csvfileId == null || csvfileId.isBlank()) return false;
        if (analysisType == null || analysisType.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
