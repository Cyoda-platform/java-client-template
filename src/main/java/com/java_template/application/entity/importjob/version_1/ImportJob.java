package com.java_template.application.entity.importjob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Object payload; // the JSON payload to ingest; can contain one item or an array
    private String source; // optional source/origin information
    private String createdAt; // ISO timestamp job created
    private String status; // PENDING / VALIDATING / PROCESSING / COMPLETED / FAILED
    private Integer itemsCreatedCount; // how many HNItem persisted
    private Integer itemsUpdatedCount; // how many HNItem were merged/updated
    private Integer itemsIgnoredCount; // how many incoming items were ignored due to exact duplicate
    private Object processingDetails; // optional detailed per-item processing outcome for audit
    private String errorMessage; // failure reason if any

    public ImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: payload must be present
        return this.payload != null;
    }
}
