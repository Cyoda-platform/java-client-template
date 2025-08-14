package com.java_template.application.entity.datadownloadjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DataDownloadJob implements CyodaEntity {
    public static final String ENTITY_NAME = "DataDownloadJob";
    public static final Integer ENTITY_VERSION = 1;

    private String url;
    private String status;
    private String createdAt;
    private String completedAt;

    public DataDownloadJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return url != null && !url.isBlank()
                && status != null && !status.isBlank()
                && createdAt != null && !createdAt.isBlank();
    }
}
