package com.java_template.application.entity.postversion.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class PostVersion implements CyodaEntity {
    public static final String ENTITY_NAME = "PostVersion";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Foreign key references and identifiers (serialized UUIDs as Strings)
    private String version_id;
    private String post_id;
    private String author_id;

    // Metadata
    private String created_at;
    private String change_summary;

    // Content fields
    private String content_rich;
    private String normalized_text;
    private String embeddings_ref;

    // Structured chunks meta
    private List<ChunkMeta> chunks_meta;

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
        // Required identifiers and timestamps must be present
        if (version_id == null || version_id.isBlank()) return false;
        if (post_id == null || post_id.isBlank()) return false;
        if (created_at == null || created_at.isBlank()) return false;

        // If chunks_meta provided, each chunk must have required fields
        if (chunks_meta != null) {
            for (ChunkMeta cm : chunks_meta) {
                if (cm == null) return false;
                if (cm.getChunk_ref() == null || cm.getChunk_ref().isBlank()) return false;
                if (cm.getText() == null) return false; // text may be blank but should not be null
            }
        }

        return true;
    }

    @Data
    public static class ChunkMeta {
        private String chunk_ref;
        private String text;
    }
}