package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.Objects;

@Data
public class ProductUploadJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ProductUploadJob";

    private String jobName;
    private String csvData;
    private String createdBy;
    private String status;
    private String createdAt;

    public ProductUploadJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return jobName != null && !jobName.isBlank()
            && csvData != null && !csvData.isBlank()
            && createdBy != null && !createdBy.isBlank()
            && status != null && !status.isBlank()
            && createdAt != null && !createdAt.isBlank();
    }
}
