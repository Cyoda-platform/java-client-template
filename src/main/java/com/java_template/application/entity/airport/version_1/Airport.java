package com.java_template.application.entity.airport.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Airport implements CyodaEntity {
    public static final String ENTITY_NAME = "Airport";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String airportCode; // IATA code
    private String name; // airport full name
    private String city;
    private String country;
    private String timezone;

    public Airport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (isBlank(airportCode)) return false;
        if (isBlank(name)) return false;
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
