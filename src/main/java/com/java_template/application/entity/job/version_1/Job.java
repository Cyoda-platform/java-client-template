package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    
    private String id; // serialized UUID for the job
    private String createdTimestamp; // timestamp when job is created
    private String updatedTimestamp; // timestamp when job is updated
    private String status; // status of the job (e.g., SCHEDULED)
    
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
        return id != null && !id.isBlank() &&
               createdTimestamp != null && !createdTimestamp.isBlank() &&
               updatedTimestamp != null && !updatedTimestamp.isBlank() &&
               status != null && !status.isBlank();
    }
}