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
    // Fields based on prototype example
    private Integer id; // example: 853
    private String firstname; // example: "Akira"
    private String surname; // example: "Suzuki"
    private Integer ageAtAward; // example: 80
    private String born; // example: "1940-09-12"
    private String bornCity; // example key: "borncity"
    private String bornCountry; // example key: "borncountry"
    private String bornCountryCode; // example key: "borncountrycode"
    private String category; // example: "Chemistry"
    private String died; // example: null
    private String gender; // example: "male"
    private String lastUpdatedAt; // example: "2025-08-01T12:00:00Z"
    private String motivation; // example: "for development..."
    private String normalizedCountryCode; // example: "JP"
    private String sourceSnapshot; // JSON string snapshot
    private String year; // example: "2010"
    private String affiliationName; // example: "Tohoku University"
    private String affiliationCity; // example: "Sendai"
    private String affiliationCountry; // example: "Japan"

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
        // id must be present
        if (id == null) return false;
        // required string fields: firstname, surname, category, year
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        // if provided, ensure ageAtAward is non-negative
        if (ageAtAward != null && ageAtAward < 0) return false;
        // optional string fields should be blank-checked if present (no further enforcement)
        // valid
        return true;
    }
}