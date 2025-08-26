package com.java_template.application.entity.weeklysendjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class WeeklySendJob implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklySendJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Entity fields based on prototype
    // Technical id (returned on POST)
    private String id;
    // Foreign key reference to CatFact (serialized UUID or identifier)
    private String catfactRef;
    private String jobName;
    // ISO-8601 timestamp as String
    private String scheduledDate;
    // Status as String (enum represented as String)
    private String status;
    private Integer targetCount;

    public WeeklySendJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required String fields using isBlank()
        if (catfactRef == null || catfactRef.isBlank()) return false;
        if (jobName == null || jobName.isBlank()) return false;
        if (scheduledDate == null || scheduledDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Validate numeric fields
        if (targetCount == null || targetCount < 0) return false;
        return true;
    }
}