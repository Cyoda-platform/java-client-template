package com.java_template.application.entity.coverphoto.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class CoverPhoto implements CyodaEntity {
    public static final String ENTITY_NAME = "CoverPhoto";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or similar)
    private String title;
    private String description;
    private String sourceUrl;
    private String thumbnailUrl;
    private String ingestionStatus;
    private String createdAt; // ISO-8601 string
    private String updatedAt; // ISO-8601 string
    private String publishedDate; // ISO-8601 string
    private List<String> tags;
    private Integer viewCount;
    private List<Comment> comments;

    @Data
    public static class Comment {
        private String createdAt;
        private String status;
        private String text;
        private String userId;
    }

    public CoverPhoto() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields: id, title, sourceUrl, thumbnailUrl, ingestionStatus, createdAt
        if (id == null || id.isBlank()) return false;
        if (title == null || title.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) return false;
        if (ingestionStatus == null || ingestionStatus.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // If updatedAt or publishedDate provided, they must be non-blank
        if (updatedAt != null && updatedAt.isBlank()) return false;
        if (publishedDate != null && publishedDate.isBlank()) return false;

        // tags if present must not contain blank values
        if (tags != null) {
            for (String t : tags) {
                if (t == null || t.isBlank()) return false;
            }
        }

        // viewCount if present must be non-negative
        if (viewCount != null && viewCount < 0) return false;

        // comments validation
        if (comments != null) {
            for (Comment c : comments) {
                if (c == null) return false;
                if (c.getUserId() == null || c.getUserId().isBlank()) return false;
                if (c.getText() == null || c.getText().isBlank()) return false;
                if (c.getCreatedAt() == null || c.getCreatedAt().isBlank()) return false;
                // status may be optional but if present must not be blank
                if (c.getStatus() != null && c.getStatus().isBlank()) return false;
            }
        }

        return true;
    }
}