package com.java_template.application.entity.media.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Media implements CyodaEntity {
    public static final String ENTITY_NAME = "Media"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String media_id;    // serialized UUID/string identifier for media
    private String owner_id;    // serialized UUID/string reference to User
    private String cdn_ref;     // CDN reference/URL
    private String filename;
    private String mime;
    private String created_at;  // ISO datetime as String
    private String status;
    private List<Object> versions; // list of version metadata objects

    public Media() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank (per rules)
        if (media_id == null || media_id.isBlank()) return false;
        if (filename == null || filename.isBlank()) return false;
        if (mime == null || mime.isBlank()) return false;
        if (cdn_ref == null || cdn_ref.isBlank()) return false;
        if (created_at == null || created_at.isBlank()) return false;
        // owner_id and status and versions may be optional; no further checks
        return true;
    }
}