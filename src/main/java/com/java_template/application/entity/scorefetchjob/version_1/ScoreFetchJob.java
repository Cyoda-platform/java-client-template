package com.java_template.application.entity.scorefetchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ScoreFetchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ScoreFetchJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String job_name;
    private String scheduled_for; // ISO timestamp
    private String target_date; // YYYY-MM-DD
    private String source_endpoint;
    private String status;
    private String started_at;
    private String completed_at;
    private String result_summary;
    private String raw_response; // JSON as String

    public ScoreFetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: required fields must be present and non-blank
        if (job_name == null || job_name.isBlank()) return false;
        if (target_date == null || target_date.isBlank()) return false;
        if (source_endpoint == null || source_endpoint.isBlank()) return false;
        return true;
    }
}
