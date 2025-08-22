package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String createdBy;     // who initiated the import (required)
    private String errorMessage;  // optional error message when failed
    private String payload;       // serialized payload (JSON as String)
    private Long resultItemId;    // id of the resulting item (numeric in example)
    private String status;        // status as String (e.g., "COMPLETED", "FAILED", etc.)

    public ImportJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // basic required field checks
        if (createdBy == null || createdBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;

        // conditional validations
        if ("COMPLETED".equalsIgnoreCase(status) && resultItemId == null) return false;
        if ("FAILED".equalsIgnoreCase(status) && (errorMessage == null || errorMessage.isBlank())) return false;

        return true;
    }
}