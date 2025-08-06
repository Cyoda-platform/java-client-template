package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class WeatherRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "WeatherRequest";

    private String cityName;
    private Double latitude;
    private Double longitude;
    private String requestType; // 'CURRENT' or 'FORECAST'
    private Instant requestTimestamp;

    public WeatherRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        boolean cityValid = (cityName == null || !cityName.isBlank());
        boolean latLongValid = (latitude != null && longitude != null);
        boolean requestTypeValid = requestType != null && !requestType.isBlank();
        return requestTypeValid && (cityValid || latLongValid);
    }
}
