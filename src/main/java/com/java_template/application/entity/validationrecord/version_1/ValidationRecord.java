package com.java_template.application.entity.validationrecord.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class ValidationRecord implements CyodaEntity {
    public static final String ENTITY_NAME = "ValidationRecord";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long hnItemId; // if present in incoming data
    private String technicalId; // record technical id
    private Boolean isValid; // validation result
    private List<String> missingFields;
    private String checkedAt; // ISO8601
    private String createdAt; // ISO8601 when record was created
    private String message; // explanation

    public ValidationRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // technicalId and checkedAt should be present
        if (this.technicalId == null || this.technicalId.isBlank()) return false;
        if (this.checkedAt == null || this.checkedAt.isBlank()) return false;
        return true;
    }
}
