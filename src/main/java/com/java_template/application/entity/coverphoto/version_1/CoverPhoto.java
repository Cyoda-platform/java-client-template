package com.java_template.application.entity.coverphoto.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CoverPhoto implements CyodaEntity {
    public static final String ENTITY_NAME = "CoverPhoto";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String coverId;
    private String title;
    private String bookId;
    private String imageUrl;
    private OffsetDateTime fetchedAt;
    private List<String> tags;
    private String status;
    private Map<String, Object> metadata;
    private Map<String, Object> originPayload;
    private String duplicateOf;
    private String ingestionJobId;
    private Boolean errorFlag;
    private Integer processingAttempts;
    private OffsetDateTime lastProcessedAt;

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
        if (this.coverId == null || this.coverId.isBlank()) {
            return false;
        }
        if (this.title == null || this.title.isBlank()) {
            return false;
        }
        if (this.imageUrl == null || this.imageUrl.isBlank()) {
            return false;
        }
        return true;
    }
}