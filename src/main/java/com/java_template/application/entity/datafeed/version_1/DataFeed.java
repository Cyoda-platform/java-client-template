package com.java_template.application.entity.datafeed.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class DataFeed implements CyodaEntity {
    public static final String ENTITY_NAME = "DataFeed";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id, e.g., "df_12345"
    private String name;
    private String url;
    private String status; // use String for enums
    private String createdAt; // ISO timestamp
    private String updatedAt; // ISO timestamp
    private String lastFetchedAt; // ISO timestamp
    private String lastChecksum;
    private Integer recordCount;
    private Map<String, String> schemaPreview; // field name -> type

    public DataFeed() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields (null or blank -> invalid)
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (url == null || url.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (updatedAt == null || updatedAt.isBlank()) return false;

        // recordCount must be present and non-negative
        if (recordCount == null || recordCount < 0) return false;

        // schemaPreview should be present with at least one entry
        if (schemaPreview == null || schemaPreview.isEmpty()) return false;

        return true;
    }
}