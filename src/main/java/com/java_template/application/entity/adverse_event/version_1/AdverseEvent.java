package com.java_template.application.entity.adverse_event.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AdverseEvent entity for AE/SAE tracking
 * Captures adverse events with severity, relatedness, and follow-up requirements
 */
@Data
public class AdverseEvent implements CyodaEntity {
    public static final String ENTITY_NAME = AdverseEvent.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String adverseEventId;
    
    // Subject association
    private String subjectId;
    
    // AE details
    private LocalDate onsetDate;
    private String seriousness; // non_serious, serious
    private String severity; // mild, moderate, severe
    private String relatedness; // not_related, unlikely, possible, probable, definite, unknown
    private String outcome; // recovered, recovering, not_recovered, fatal, unknown
    private String actionTaken;
    private String narrative;
    
    // SAE handling
    private Boolean isSAE;
    private LocalDate followUpDueDate;
    
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
        return adverseEventId != null && !adverseEventId.trim().isEmpty() &&
               subjectId != null && !subjectId.trim().isEmpty() &&
               onsetDate != null &&
               seriousness != null && !seriousness.trim().isEmpty() &&
               severity != null && !severity.trim().isEmpty();
    }
}
