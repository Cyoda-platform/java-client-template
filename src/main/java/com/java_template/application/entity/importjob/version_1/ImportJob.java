package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;
import java.util.HashMap;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;

    // Fields
    private String id; // UUID as string
    private String type; // User|Product
    private String source; // CSV|JSON|API
    private String uploadedBy; // User.id as string
    private String status; // Pending|Processing|Completed|Failed
    private String fileUrl;
    private String createdAt; // ISO8601
    private String completedAt; // ISO8601
    private Map<String, Object> resultSummary = new HashMap<>();

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
        return id != null && !id.isBlank()
            && type != null && !type.isBlank()
            && source != null && !source.isBlank()
            && uploadedBy != null && !uploadedBy.isBlank()
            && status != null && !status.isBlank();
    }
}
