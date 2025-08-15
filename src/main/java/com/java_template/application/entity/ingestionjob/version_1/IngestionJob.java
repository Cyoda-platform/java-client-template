package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // business id
    private String sourceUrl; // Petstore source
    private String requestedBy; // user id (serialized UUID)
    private String requestedAt; // datetime as ISO-8601 string
    private String status; // pending/running/completed/failed
    private Integer importedCount;
    private List<String> errors = new ArrayList<>();

    // Additional tracking fields expected by processors
    private String createdAt;
    private String updatedAt;

    public IngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (importedCount != null && importedCount < 0) return false;
        return true;
    }
}
