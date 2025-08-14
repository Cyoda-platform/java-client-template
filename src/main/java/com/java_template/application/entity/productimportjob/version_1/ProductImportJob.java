package com.java_template.application.entity.productimportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ProductImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ProductImportJob";
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String status;
    private String errorMessages;

    public ProductImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && status != null && !status.isBlank();
    }
}}