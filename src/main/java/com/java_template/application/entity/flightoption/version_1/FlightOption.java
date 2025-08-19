package com.java_template.application.entity.flightoption.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class FlightOption implements CyodaEntity {
    public static final String ENTITY_NAME = "FlightOption";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String optionId; // unique id for this flight option
    private String searchId; // links to FlightSearch
    private String airline;
    private String flightNumber;
    private String departureTime; // ISO datetime
    private String arrivalTime; // ISO datetime
    private Integer durationMinutes;
    private Double priceAmount;
    private String currency;
    private Integer stops;
    private String layovers; // short description
    private String fareRules; // summary
    private Integer seatAvailability;
    private String status; // e.g., CREATED|READY|UNAVAILABLE|ARCHIVED

    public FlightOption() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: optionId, searchId, airline, departureTime, arrivalTime should be present
        if (isBlank(optionId)) return false;
        if (isBlank(searchId)) return false;
        if (isBlank(airline)) return false;
        if (isBlank(departureTime)) return false;
        if (isBlank(arrivalTime)) return false;
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
