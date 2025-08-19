package com.java_template.application.entity.flightsearch.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class FlightSearch implements CyodaEntity {
    public static final String ENTITY_NAME = "FlightSearch";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String searchId; // unique business id for the search
    private String originAirportCode; // IATA code user entered
    private String destinationAirportCode; // IATA code user entered
    private String departureDate; // ISO date
    private String returnDate; // ISO date, optional
    private Integer passengerCount; // number of passengers
    private String cabinClass; // optional
    private String createdAt; // ISO timestamp
    private String status; // PENDING|SUCCESS|NO_RESULTS|ERROR
    private String errorMessage; // optional, populated on ERROR

    public FlightSearch() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (originAirportCode == null || originAirportCode.isBlank()) return false;
        if (destinationAirportCode == null || destinationAirportCode.isBlank()) return false;
        if (departureDate == null || departureDate.isBlank()) return false;
        if (passengerCount == null || passengerCount < 1) return false;
        return true;
    }
}
