package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import jakarta.validation.constraints.*;

@Data
public class FlightSearchRequest implements CyodaEntity {

    @NotBlank
    private String departureAirport;

    @NotBlank
    private String arrivalAirport;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String departureDate;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String returnDate;

    @Min(1)
    private int passengers;

    public FlightSearchRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("flightSearchRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "flightSearchRequest");
    }

    @Override
    public boolean isValid() {
        return departureAirport != null && !departureAirport.isBlank()
                && arrivalAirport != null && !arrivalAirport.isBlank()
                && departureDate != null && departureDate.matches("\\d{4}-\\d{2}-\\d{2}")
                && passengers >= 1
                && (returnDate == null || returnDate.matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
