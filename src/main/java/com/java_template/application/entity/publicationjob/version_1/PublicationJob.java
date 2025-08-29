package com.java_template.application.entity.publicationjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class PublicationJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PublicationJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID as String)
    private String id;
    // Timestamps as ISO-8601 strings
    private String createdAt;
    private String scheduledAt;
    // Business fields
    private String jobName;
    private Integer priority;
    // Use String for enum-like field
    private String status;
    // Foreign key reference (serialized UUID)
    private String targetArticleId;

    public PublicationJob() {} 

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
        if (jobName == null || jobName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (targetArticleId == null || targetArticleId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        // Validate priority
        if (priority == null) return false;
        return true;
    }
}