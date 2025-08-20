package com.java_template.application.entity.coverphoto.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class CoverPhoto implements CyodaEntity {
    public static final String ENTITY_NAME = "CoverPhoto";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // platform assigned technical id
    private String coverId; // origin identifier from source
    private String title; // display title
    private String bookId; // related book id from source
    private String imageUrl; // location of image
    private Instant fetchedAt; // timestamp when fetched
    private List<String> tags; // classification tags
    private String status; // active/archived/failed etc
    private Map<String, Object> metadata; // dimensions, size, mimeType
    private Map<String, Object> originPayload; // raw source payload
    private String duplicateOf; // coverId it duplicates or null
    private String ingestionJobId; // technicalId of the ingestion job that created it
    private Boolean errorFlag; // true when validation/processing failed
    private Integer processingAttempts; // retry counter
    private Instant lastProcessedAt; // last processing timestamp

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
        // Basic validation: required string fields must be present and not blank
        if (this.coverId == null || this.coverId.isBlank()) return false;
        if (this.title == null || this.title.isBlank()) return false;
        if (this.imageUrl == null || this.imageUrl.isBlank()) return false;
        // status should be present
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
