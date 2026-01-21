package com.java_template.application.entity.manualusersync.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * ManualUserSync Entity - Represents a manual user synchronization run from Auth0
 * 
 * This entity tracks the state and results of a manual sync operation,
 * including counters for created/updated/missing users and error tracking.
 */
@Data
public class ManualUserSync implements CyodaEntity {
    public static final String ENTITY_NAME = "ManualUserSync";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Sync metadata
    private String name;
    private String description;
    private String initiatedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String status;

    // Sync parameters
    private SyncParameters syncParameters;

    // Sync results
    private SyncResults results;

    // Pagination state
    private String auth0FetchCursor;

    // Audit fields
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return id != null && !id.isBlank();
    }

    /**
     * Sync parameters configuration
     */
    @Data
    public static class SyncParameters {
        private String auth0Domain;
        private Boolean includeUserMetadata;
        private Integer pageSize;
    }

    /**
     * Sync results tracking
     */
    @Data
    public static class SyncResults {
        private Integer created;
        private Integer updated;
        private Integer missing;
        private List<String> errors;
    }
}

