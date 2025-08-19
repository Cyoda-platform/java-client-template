package com.java_template.application.entity.flightoption.version_1;

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
    private String status; // CREATED|ENRICHING|AVAILABILITY_CHECK|READY|UNAVAILABLE|ARCHIVED

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
        if (optionId == null || optionId.isBlank()) return false;
        if (searchId == null || searchId.isBlank()) return false;
        if (airline == null || airline.isBlank()) return false;
        if (flightNumber == null || flightNumber.isBlank()) return false;
        if (departureTime == null || departureTime.isBlank()) return false;
        if (arrivalTime == null || arrivalTime.isBlank()) return false;
        if (durationMinutes == null || durationMinutes < 0) return false;
        if (priceAmount == null || priceAmount < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        return true;
    }
}
