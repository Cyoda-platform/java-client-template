package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Objects;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // business id
    private String importType; // users or products
    private String sourceLocation; // file path or url
    private String uploadedBy; // admin user id (serialized UUID)
    private String uploadedAt; // DateTime as ISO string
    private String status; // created validating processing completed failed
    private Summary summary;

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
        if (importType == null || importType.isBlank()) return false;
        if (sourceLocation == null || sourceLocation.isBlank()) return false;
        if (uploadedBy == null || uploadedBy.isBlank()) return false;
        return true;
    }

    @Data
    public static class Summary {
        private Integer rowsProcessed;
        private Integer rowsCreated;
        private Integer rowsUpdated;
        private Integer rowsFailed;

        public Summary() {}
    }
}
