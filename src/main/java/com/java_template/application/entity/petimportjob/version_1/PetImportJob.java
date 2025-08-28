package com.java_template.application.entity.petimportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class PetImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetImportJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String jobId;         // technical id
    private String sourceUrl;
    private String requestedAt;   // ISO-8601 timestamp as String
    private String status;
    private Integer fetchedCount;
    private Integer createdCount;
    private String error;         // error message, may be null

    public PetImportJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank() checks
        if (jobId == null || jobId.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;

        // Validate counts
        if (fetchedCount == null || fetchedCount < 0) return false;
        if (createdCount == null || createdCount < 0) return false;

        // error may be null or blank; no validation required

        return true;
    }
}