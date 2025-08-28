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
    private String firstname;
    private String surname;
    private String gender;
    private String motivation;
    private String category;
    private String year;
    private String born;
    private String died;
    private String borncity;
    private String borncountry;
    private String borncountrycode;
    private String affiliation_name;
    private String affiliation_city;
    private String affiliation_country;
    private Integer derived_ageAtAward;
    private String normalizedCountryCode;

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
        // Required fields: id, firstname, surname, category, year
        if (this.id == null) return false;
        if (this.firstname == null || this.firstname.isBlank()) return false;
        if (this.surname == null || this.surname.isBlank()) return false;
        if (this.category == null || this.category.isBlank()) return false;
        if (this.year == null || this.year.isBlank()) return false;
        return true;
    }
}