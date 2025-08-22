package com.java_template.application.entity.petsyncjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class PetSyncJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetSyncJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private Map<String, Object> config; // flexible structure for filters, sourceUrl, etc.
    private String startTime;
    private String endTime;
    private String errorMessage;
    private Integer fetchedCount;
    private String source;
    private String status;

    public PetSyncJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (startTime == null || startTime.isBlank()) return false;

        // Config must be provided
        if (config == null) return false;

        // fetchedCount, if present, must be non-negative
        if (fetchedCount != null && fetchedCount < 0) return false;

        return true;
    }
}