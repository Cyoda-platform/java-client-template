package com.java_template.application.entity.location.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Location implements CyodaEntity {
    public static final String ENTITY_NAME = "Location"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String locationId;
    private String name;
    private String region;
    private String timezone;
    private Boolean active;
    private Double latitude;
    private Double longitude;

    public Location() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields
        if (locationId == null || locationId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;

        // Validate numeric and boolean fields
        if (active == null) return false;
        if (latitude == null || longitude == null) return false;
        if (latitude < -90.0 || latitude > 90.0) return false;
        if (longitude < -180.0 || longitude > 180.0) return false;

        return true;
    }
}