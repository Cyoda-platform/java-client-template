package com.java_template.application.entity.datasource.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * DataSource Entity
 * Represents a CSV data source that can be downloaded from a URL for analysis.
 */
@Data
public class DataSource implements CyodaEntity {
    public static final String ENTITY_NAME = DataSource.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String sourceId;
    
    // Required core business fields
    private String url;
    private String fileName;
    
    // Optional fields for additional business data
    private LocalDateTime downloadedAt;
    private Long fileSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return sourceId != null && !sourceId.trim().isEmpty() 
            && url != null && !url.trim().isEmpty();
    }
}
