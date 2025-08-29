package com.java_template.application.entity.adoptionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID)
    private String jobName;
    private String jobType; // use String for enum-like values
    private String petId; // foreign key as serialized UUID
    private String requestedAt; // ISO-8601 timestamp as String
    private String resultNotes;
    private String status; // use String for enum-like values
    private String userId; // foreign key as serialized UUID

    public AdoptionJob() {} 

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
        if (jobName == null || jobName.isBlank()) return false;
        if (jobType == null || jobType.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        return true;
    }
}