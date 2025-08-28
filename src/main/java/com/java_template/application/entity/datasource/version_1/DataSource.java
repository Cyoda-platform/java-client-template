package com.java_template.application.entity.datasource.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DataSource implements CyodaEntity {
    public static final String ENTITY_NAME = "DataSource"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String lastFetchedAt;
    private String sampleHash;
    private String schema;
    private String url;
    private String validationStatus;

    public DataSource() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields (use isBlank checks)
        if (id == null || id.isBlank()) return false;
        if (url == null || url.isBlank()) return false;
        if (schema == null || schema.isBlank()) return false;
        if (sampleHash == null || sampleHash.isBlank()) return false;
        if (validationStatus == null || validationStatus.isBlank()) return false;
        // lastFetchedAt is optional (can be null/blank until first fetch)
        return true;
    }
}