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
    private String media_id;
    private String owner_id; // serialized UUID reference to User
    private String filename;
    private String mime;
    private String cdn_ref;
    private String created_at; // ISO-8601 timestamp as String
    private String status;
    private List<MediaVersion> versions;

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
        // Validate required string fields using isBlank()
        if (media_id == null || media_id.isBlank()) return false;
        if (filename == null || filename.isBlank()) return false;
        if (mime == null || mime.isBlank()) return false;
        if (created_at == null || created_at.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // owner_id is a foreign key serialized UUID; ensure not blank
        if (owner_id == null || owner_id.isBlank()) return false;
        // Validate versions if present
        if (versions != null) {
            for (MediaVersion v : versions) {
                if (v == null) return false;
                if (v.getVersion_id() == null || v.getVersion_id().isBlank()) return false;
                if (v.getFilename() == null || v.getFilename().isBlank()) return false;
            }
        }
        return true;
    }

    @Data
    public static class MediaVersion {
        private String filename;
        private String version_id;
    }
}