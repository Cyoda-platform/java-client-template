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
    private Integer id;
    private Integer ageAtAward;
    private String affiliationCity;
    private String affiliationCountry;
    private String affiliationName;
    private String born; // ISO date string e.g. 1939-09-12
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private String category;
    private String died; // may be null
    private String firstname;
    private String gender;
    private String lastSeenAt; // ISO timestamp string
    private String motivation;
    private String normalizedCountryCode;
    private String surname;
    private String validationStatus;
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
        // Basic validation: required fields must be present and non-blank where applicable
        if (this.id == null) return false;
        if (this.firstname == null || this.firstname.isBlank()) return false;
        if (this.surname == null || this.surname.isBlank()) return false;
        if (this.category == null || this.category.isBlank()) return false;
        if (this.year == null || this.year.isBlank()) return false;
        if (this.validationStatus == null || this.validationStatus.isBlank()) return false;
        return true;
    }
}