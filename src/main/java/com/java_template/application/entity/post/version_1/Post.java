package com.java_template.application.entity.post.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Post implements CyodaEntity {
    public static final String ENTITY_NAME = "Post";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID
    private String authorId; // serialized UUID
    private String ownerId; // serialized UUID
    private String currentVersionId; // serialized UUID
    private String title;
    private String slug;
    private String summary;
    private String status;
    private String locale;
    private String cacheControl;
    private String publishDatetime; // ISO datetime as String
    private String publishedAt; // ISO datetime as String
    private List<String> mediaRefs; // List of serialized UUIDs
    private List<String> tags;

    public Post() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required identifier fields must be present and non-blank
        if (id == null || id.isBlank()) return false;
        if (ownerId == null || ownerId.isBlank()) return false;
        if (authorId == null || authorId.isBlank()) return false;

        // Basic content requirements
        if (title == null || title.isBlank()) return false;
        if (slug == null || slug.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If optional string fields are provided they must not be blank
        if (currentVersionId != null && currentVersionId.isBlank()) return false;
        if (locale != null && locale.isBlank()) return false;
        if (cacheControl != null && cacheControl.isBlank()) return false;
        if (publishDatetime != null && publishDatetime.isBlank()) return false;
        if (publishedAt != null && publishedAt.isBlank()) return false;
        if (summary != null && summary.isBlank()) return false;

        // Validate lists: if present, none of their entries should be null/blank
        if (mediaRefs != null) {
            for (String m : mediaRefs) {
                if (m == null || m.isBlank()) return false;
            }
        }
        if (tags != null) {
            for (String t : tags) {
                if (t == null || t.isBlank()) return false;
            }
        }

        return true;
    }
}