package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private List<String> affiliations;
    private String category;
    private String changeType;
    private String country;
    private String detectedAt; // ISO-8601 timestamp as String
    private String fullName;
    private String laureateId; // technical id (serialized UUID or string id)
    private Integer prizeYear;
    private Boolean published;
    private String rawPayload; // original payload as JSON string

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
        // Required fields: laureateId, fullName, prizeYear, detectedAt
        if (laureateId == null || laureateId.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (prizeYear == null) return false;
        if (detectedAt == null || detectedAt.isBlank()) return false;

        // If rawPayload provided, it must not be blank
        if (rawPayload != null && rawPayload.isBlank()) return false;

        // If affiliations provided, ensure no blank entries
        if (affiliations != null) {
            for (String a : affiliations) {
                if (a == null || a.isBlank()) return false;
            }
        }

        return true;
    }
}