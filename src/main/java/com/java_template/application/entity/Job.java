package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    
    private String jobId;
    private String scheduleDetails;
    private String status;
    private String jobType; // new field
    private String createdBy; // new field

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobId == null || jobId.isBlank()) return false;
        if (scheduleDetails == null || scheduleDetails.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (jobType == null || jobType.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;
        return true;
    }
}
