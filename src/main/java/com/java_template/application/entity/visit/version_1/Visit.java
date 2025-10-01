package com.java_template.application.entity.visit.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Visit entity for tracking subject visits
 * Manages planned vs actual visits with deviations and CRF data
 */
@Data
public class Visit implements CyodaEntity {
    public static final String ENTITY_NAME = Visit.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String visitId;
    
    // Subject association
    private String subjectId;
    
    // Visit details
    private String visitCode;
    private LocalDate plannedDate;
    private LocalDate actualDate;
    private String status; // planned, completed, missed
    
    // Protocol deviations
    private List<ProtocolDeviation> deviations;
    
    // CRF data (flexible JSON structure)
    private Map<String, Object> crfData;
    private Boolean locked;
    
    // Audit fields
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
        return visitId != null && !visitId.trim().isEmpty() &&
               subjectId != null && !subjectId.trim().isEmpty() &&
               visitCode != null && !visitCode.trim().isEmpty();
    }

    /**
     * Nested class for protocol deviations
     */
    @Data
    public static class ProtocolDeviation {
        private String code;
        private String description;
        private String severity; // minor, major
    }
}
