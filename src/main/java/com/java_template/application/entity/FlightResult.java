package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class FlightResult implements CyodaEntity {

    private String flightNumber;
    private String airline;
    private String departureAirport;
    private String arrivalAirport;
    private String departureTime;
    private String arrivalTime;
    private Double price;

    public FlightResult() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("flightResult");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "flightResult");
    }

    @Override
    public boolean isValid() {
        return flightNumber != null && !flightNumber.isBlank()
                && airline != null && !airline.isBlank()
                && departureAirport != null && !departureAirport.isBlank()
                && arrivalAirport != null && !arrivalAirport.isBlank()
                && departureTime != null && !departureTime.isBlank()
                && arrivalTime != null && !arrivalTime.isBlank()
                && price != null && price >= 0;
    }
}
