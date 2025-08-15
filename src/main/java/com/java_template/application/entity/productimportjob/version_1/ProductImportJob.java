package com.java_template.application.entity.productimportjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ProductImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ProductImportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String jobId;
    private String sourceType; // CSV|S3|API
    private String sourceLocation;
    private String initiatedBy; // User.id as String
    private String fileChecksum;
    private String status; // PENDING, VALIDATING, PROCESSING, COMPLETED, FAILED
    private String createdAt; // ISO8601
    private String completedAt; // ISO8601

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
        // Basic validation: required fields must not be blank
        if (jobId == null || jobId.isBlank()) return false;
        if (sourceType == null || sourceType.isBlank()) return false;
        if (sourceLocation == null || sourceLocation.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}
