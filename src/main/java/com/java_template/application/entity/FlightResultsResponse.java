package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class FlightResultsResponse implements CyodaEntity {

    private String searchId;
    private List<FlightResult> flights;

    public FlightResultsResponse() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("flightResultsResponse");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "flightResultsResponse");
    }

    @Override
    public boolean isValid() {
        return searchId != null && !searchId.isBlank() && flights != null;
    }
}
