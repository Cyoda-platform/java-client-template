package com.java_template.application.entity.datasource.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * DataSource Entity
 * 
 * Represents a CSV data source that can be downloaded and analyzed. 
 * Manages the lifecycle of data fetching and processing.
 */
@Data
public class DataSource implements CyodaEntity {
    public static final String ENTITY_NAME = DataSource.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String dataSourceId;
    
    // Required core business fields
    private String url;
    private String name;
    
    // Optional fields for additional business data
    private String description;
    private LocalDateTime lastFetchTime;
    private LocalDateTime lastAnalysisTime;
    private Integer recordCount;
    private Long fileSize;
    private String checksum;

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
        return dataSourceId != null && 
               url != null && 
               name != null && !name.trim().isEmpty() &&
               isValidUrl(url);
    }
    
    /**
     * Validates if the URL is a valid HTTP/HTTPS URL
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
