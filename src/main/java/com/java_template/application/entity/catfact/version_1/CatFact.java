package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Objects;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (serialized UUID) - returned by POST endpoints as a technical id
    private String technicalId;

    // The fact text
    private String text;

    // Source of the fact (e.g., "catfact.ninja")
    private String source;

    // ISO-8601 timestamp when the fact was fetched (e.g., "2025-09-07T09:00:01Z")
    private String fetchedAt;

    // How many times this fact has been sent
    private Integer sendCount;

    // Engagement score (e.g., 12.5)
    private Double engagementScore;

    // Validation status (use String for enum-like values, e.g., "VALID")
    private String validationStatus;

    public CatFact() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must not be blank
        if (text == null || text.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (fetchedAt == null || fetchedAt.isBlank()) return false;
        if (validationStatus == null || validationStatus.isBlank()) return false;

        // Numeric fields must be present and non-negative where applicable
        if (Objects.isNull(sendCount) || sendCount < 0) return false;
        if (Objects.isNull(engagementScore)) return false;

        return true;
    }
}