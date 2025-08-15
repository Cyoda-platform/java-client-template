package com.java_template.application.entity.hackernewsitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;

@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";
    public static final Integer ENTITY_VERSION = 1;

    // Fields as defined in the functional requirements
    private String originalJson; // the original Firebase-format Hacker News JSON exactly as received
    private Long id; // the Hacker News item id extracted from originalJson, if present
    private String type; // the Hacker News item type extracted from originalJson, if present
    private Instant importTimestamp; // enriched timestamp when the item was imported; kept separate from the original JSON
    private String state; // VALID or INVALID; assigned after validation that fields id and type are present
    private String validationErrors; // optional short message describing why state = INVALID
    private Instant createdAt; // when this entity record was created in the datastore

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: originalJson must be present. Detailed validation of id/type is handled by processing logic.
        if (this.originalJson == null || this.originalJson.isBlank()) return false;
        return true;
    }
}
