package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id returned by POST endpoints
    private String id;

    // ISO-8601 timestamp string
    private String createdAt;

    private String jobName;
    private List<String> locations;
    private Map<String, Object> parameters;
    private String schedule;
    private String source;
    private String status;

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
        // Validate required string fields using isBlank()
        if (createdAt == null || createdAt.isBlank()) return false;
        if (jobName == null || jobName.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // locations must be present and contain non-blank entries
        if (locations == null || locations.isEmpty()) return false;
        for (String loc : locations) {
            if (loc == null || lo c.isBlank()) return false;
        }

        // parameters may be null or empty; no strict check here

        return true;
    }
}