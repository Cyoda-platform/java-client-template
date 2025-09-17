package com.java_template.application.entity.dataanalysis.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * DataAnalysis Entity
 * Handles analysis of downloaded CSV data using pandas-style operations to generate insights and reports.
 */
@Data
public class DataAnalysis implements CyodaEntity {
    public static final String ENTITY_NAME = DataAnalysis.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String analysisId;
    
    // Required core business fields
    private String dataSourceId;
    private String analysisType;
    
    // Optional fields for additional business data
    private String reportData;
    private LocalDateTime analysisStartedAt;
    private LocalDateTime analysisCompletedAt;
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
        return analysisId != null && !analysisId.trim().isEmpty() 
            && dataSourceId != null && !dataSourceId.trim().isEmpty()
            && analysisType != null && !analysisType.trim().isEmpty();
    }
}
