package com.java_template.application.entity.ingestjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class IngestJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // system-generated unique id for this stored record
    private String source; // origin of job e.g. manual, api
    private String payload; // optional bulk JSON payload
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601 last update time
    private String status; // PENDING RUNNING COMPLETED FAILED
    private List<String> createdItemTechnicalIds; // technicalIds of created HNItem entities

    public IngestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: source and status must be present and not blank
        if (this.source == null || this.source.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
