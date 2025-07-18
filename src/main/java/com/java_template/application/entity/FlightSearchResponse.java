package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class FlightSearchResponse implements CyodaEntity {

    private String searchId;
    private int resultsCount;
    private String message;

    public FlightSearchResponse() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("flightSearchResponse");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "flightSearchResponse");
    }

    @Override
    public boolean isValid() {
        return searchId != null && !searchId.isBlank() && resultsCount >= 0;
    }
}
