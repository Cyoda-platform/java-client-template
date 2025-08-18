package com.java_template.application.entity.retrievaljob.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class RetrievalJob implements CyodaEntity {
    public static final String ENTITY_NAME = "RetrievalJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long itemId; // HN item id requested
    private String createdAt; // ISO timestamp
    private String status; // PENDING / LOOKUP / FOUND / NOT_FOUND / FAILED
    private Object result; // when FOUND, contains rawJson returned
    private String errorMessage;

    public RetrievalJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // itemId is required for retrieval
        return this.itemId != null && this.itemId > 0;
    }
}
