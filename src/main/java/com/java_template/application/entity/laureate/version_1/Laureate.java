package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id returned by POST endpoints (serialized UUID)
    private String id;

    private String externalId;
    private String fullName;
    private String changeSummary;
    private String country;
    private String birthDate; // ISO date e.g. 1867-11-07
    private String motivation;
    private String prizeCategory;
    private Integer prizeYear;
    private String rawPayload; // serialized JSON payload
    private String firstSeenTimestamp; // ISO timestamp
    private String lastSeenTimestamp; // ISO timestamp

    public Laureate() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be non-blank
        if (externalId == null || externalId.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (prizeCategory == null || prizeCategory.isBlank()) return false;
        // prizeYear must be present
        if (prizeYear == null) return false;
        // If provided, timestamps and rawPayload must not be blank
        if (firstSeenTimestamp != null && firstSeenTimestamp.isBlank()) return false;
        if (lastSeenTimestamp != null && lastSeenTimestamp.isBlank()) return false;
        if (rawPayload != null && rawPayload.isBlank()) return false;
        return true;
    }
}