package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID)
    private String id;
    private Boolean enabled;
    private String lastResultSummary;
    private String lastRunTimestamp;
    private String name;
    private String scheduleSpec;
    private String scheduleType; // use String for enum-like values
    private String sourceEndpoint; // reference to source as serialized UUID or string identifier
    private String status; // use String for enum-like values

    public Job() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (name == null || name.isBlank()) return false;
        if (scheduleSpec == null || scheduleSpec.isBlank()) return false;
        if (scheduleType == null || scheduleType.isBlank()) return false;
        if (sourceEndpoint == null || sourceEndpoint.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // enabled should be provided
        if (enabled == null) return false;
        // lastRunTimestamp and lastResultSummary are optional
        return true;
    }
}