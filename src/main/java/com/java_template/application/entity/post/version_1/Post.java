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

    // Entity fields based on prototype
    // Use String for UUID/foreign key references (serialized UUIDs)
    private String id;
    private String author_id;
    private String owner_id;
    private String current_version_id;
    private String cache_control;
    private String locale;
    private String publish_datetime;
    private String published_at;
    private String slug;
    private String status;
    private String summary;
    private String title;
    private List<String> media_refs;
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
        // Basic validation: required string fields must be present and not blank.
        if (id == null || id.isBlank()) return false;
        if (owner_id == null || owner_id.isBlank()) return false;
        if (title == null || title.isBlank()) return false;
        if (slug == null || slug.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Optional fields (author_id, current_version_id, publish_datetime, etc.) may be blank/null.
        return true;
    }
}