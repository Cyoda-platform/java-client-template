package com.java_template.application.entity.petimportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class PetImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetImportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String errors;
    private Integer importedCount;
    private String requestId;
    private String requestedAt;
    private String sourceUrl;
    private String status;

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
        // requestId, requestedAt, sourceUrl and status must be present
        if (requestId == null || requestId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // importedCount must be non-null and non-negative
        if (importedCount == null || importedCount < 0) return false;
        // errors may be empty or null
        return true;
    }
}