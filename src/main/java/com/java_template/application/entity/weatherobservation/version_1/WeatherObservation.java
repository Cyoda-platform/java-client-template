package com.java_template.application.entity.weatherobservation.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class WeatherObservation implements CyodaEntity {
    public static final String ENTITY_NAME = "WeatherObservation"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Identifiers
    private String observationId; // technical id for the observation
    private String locationId; // foreign key reference (serialized UUID or location identifier)
    private String rawSourceId; // id from raw/source system

    // Observation data
    private Integer humidity; // percentage 0-100
    private Double precipitation; // mm or relevant unit
    private Double temperature; // degrees Celsius
    private Double windSpeed; // m/s or relevant unit

    // Metadata
    private String timestamp; // ISO-8601 string of observation time
    private Boolean processed; // whether the observation has been processed

    public WeatherObservation() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must be present and not blank
        if (this.observationId == null || this.observationId.isBlank()) return false;
        if (this.locationId == null || this.locationId.isBlank()) return false;
        if (this.timestamp == null || this.timestamp.isBlank()) return false;

        // processed should be explicitly set
        if (this.processed == null) return false;

        // Validate humidity if provided
        if (this.humidity != null) {
            if (this.humidity < 0 || this.humidity > 100) return false;
        }

        // Validate numeric doubles if provided (not NaN/Infinite, precipitation and windSpeed non-negative)
        if (this.temperature != null) {
            if (Double.isNaN(this.temperature) || Double.isInfinite(this.temperature)) return false;
        }

        if (this.precipitation != null) {
            if (Double.isNaN(this.precipitation) || Double.isInfinite(this.precipitation)) return false;
            if (this.precipitation < 0) return false;
        }

        if (this.windSpeed != null) {
            if (Double.isNaN(this.windSpeed) || Double.isInfinite(this.windSpeed)) return false;
            if (this.windSpeed < 0) return false;
        }

        // rawSourceId is optional; if present, ensure not blank
        if (this.rawSourceId != null && this.rawSourceId.isBlank()) return false;

        return true;
    }
}