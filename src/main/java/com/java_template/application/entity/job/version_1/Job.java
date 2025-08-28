package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID or technical id
    private String createdBy;
    private String runTimestamp; // ISO-8601 string
    private String completedTimestamp; // ISO-8601 string, nullable
    private String schedule;
    private String sourceUrl;
    private String state; // use String for enum-like values
    private Summary summary;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be non-null and not blank
        if (id == null || id.isBlank()) return false;
        if (runTimestamp == null || runTimestamp.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (state == null || state.isBlank()) return false;
        // createdBy can be optional but if present must not be blank
        if (createdBy != null && createdBy.isBlank()) return false;
        // summary if present must be valid
        if (summary != null && !summary.isValid()) return false;
        return true;
    }

    @Data
    public static class Summary {
        private List<String> errors;
        private Integer failedCount;
        private Integer ingestedCount;

        public Summary() {}

        public boolean isValid() {
            if (errors == null) return false;
            if (failedCount == null || failedCount < 0) return false;
            if (ingestedCount == null || ingestedCount < 0) return false;
            return true;
        }
    }
}