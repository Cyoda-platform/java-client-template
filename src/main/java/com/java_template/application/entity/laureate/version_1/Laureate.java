package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields inferred from prototype
    private Integer id; // technical id from source
    private String firstname;
    private String surname;
    private String category;
    private String year;
    private String motivation;
    private String gender;
    private String born; // ISO date string, e.g., "1930-09-12"
    private String died; // ISO date string or null
    private String borncity;
    private String borncountry;
    private String borncountrycode;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private Integer enrichedAgeAtAward;
    private String normalizedCountryCode;
    private List<String> validationErrors;
    private String validationStatus;

    public Laureate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: required fields must be present and non-blank
        if (!Objects.nonNull(id)) {
            return false;
        }
        if (firstname == null || firstname.isBlank()) {
            return false;
        }
        if (surname == null || surname.isBlank()) {
            return false;
        }
        if (category == null || category.isBlank()) {
            return false;
        }
        if (year == null || year.isBlank()) {
            return false;
        }
        // motivation may be optional in some datasets, but if present it should not be blank
        if (motivation != null && motivation.isBlank()) {
            return false;
        }
        return true;
    }
}