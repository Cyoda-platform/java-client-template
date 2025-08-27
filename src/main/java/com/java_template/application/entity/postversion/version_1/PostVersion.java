package com.java_template.application.entity.postversion.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PostVersion implements CyodaEntity {
    public static final String ENTITY_NAME = "PostVersion";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Foreign key references and ids are represented as serialized UUID strings
    private String versionId;
    private String postId;
    private String authorId;
    private String changeSummary;
    private List<Map<String, Object>> chunksMeta;
    private String contentRich;
    private String createdAt;
    private String embeddingsRef;
    private String normalizedText;

    public PostVersion() {}

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
        if (versionId == null || versionId.isBlank()) return false;
        if (postId == null || postId.isBlank()) return false;
        if (authorId == null || authorId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // chunksMeta can be empty but should not be null if present in payload
        if (chunksMeta == null) return false;
        return true;
    }
}