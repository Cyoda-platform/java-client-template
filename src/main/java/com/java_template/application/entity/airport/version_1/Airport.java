package com.java_template.application.entity.airport.version_1;

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
        if (airportCode == null || airportCode.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (city == null || city.isBlank()) return false;
        if (country == null || country.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        return true;
    }
}
