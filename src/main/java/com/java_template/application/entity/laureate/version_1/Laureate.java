package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized)
    private String firstname;
    private String surname;
    private String gender;
    private String born; // ISO date string yyyy-MM-dd
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private Integer ageAtAward;
    private String category;
    private String died; // ISO date string yyyy-MM-dd or null
    private String motivation;
    private String normalizedCountryCode;
    private String validated;
    private String year;

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
        // Required string fields must be non-null and not blank
        if (id == null || id.isBlank()) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        // Numeric fields validation
        if (ageAtAward != null && ageAtAward < 0) return false;
        return true;
    }
}