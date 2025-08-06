package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class WeatherData implements CyodaEntity {
    public static final String ENTITY_NAME = "WeatherData";

    private String weatherRequestId; // serialized UUID
    private String dataType; // 'CURRENT' or 'FORECAST'
    private Double temperature;
    private Double humidity;
    private Double windSpeed;
    private Double precipitation;
    private Instant observationTime;

    public WeatherData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        boolean weatherRequestIdValid = weatherRequestId != null && !weatherRequestId.isBlank();
        boolean dataTypeValid = dataType != null && !dataType.isBlank();
        return weatherRequestIdValid && dataTypeValid;
    }
}
