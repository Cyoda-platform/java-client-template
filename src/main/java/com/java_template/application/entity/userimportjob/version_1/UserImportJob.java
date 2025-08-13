package com.java_template.application.entity.userimportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "UserImportJob";
    public static final Integer ENTITY_VERSION = 1;

    private String jobId;
    private String importData;
    private String status;
    private LocalDateTime createdAt;

    public UserImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return jobId != null && !jobId.isBlank() &&
               importData != null && !importData.isBlank() &&
               status != null && !status.isBlank() &&
               createdAt != null;
    }
}
