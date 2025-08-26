package com.java_template.application.entity.analysisjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AnalysisJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AnalysisJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or string)
    private String dataFeedId; // foreign key reference as serialized UUID/string
    private String reportRef; // reference to generated report (string)
    private String requestedBy; // user or email who requested the job
    private String runMode; // e.g., AD_HOC, SCHEDULED (use String for enum)
    private String scheduleSpec; // serialized schedule specification (JSON/string), nullable
    private String status; // e.g., PENDING, RUNNING, COMPLETED (use String for enum)
    private String createdAt; // ISO timestamp string
    private String startedAt; // ISO timestamp string, nullable
    private String completedAt; // ISO timestamp string, nullable

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
        // Required fields: id, createdAt, dataFeedId, runMode, status
        if (id == null || id.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (dataFeedId == null || dataFeedId.isBlank()) return false;
        if (runMode == null || runMode.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}