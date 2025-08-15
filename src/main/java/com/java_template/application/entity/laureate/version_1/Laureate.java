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
    private Integer id; // Nobel dataset id
    private String firstname;
    private String surname;
    private String born; // birth date e.g., "1930-09-12"
    private String died; // death date or null
    private String borncountry;
    private String borncountrycode;
    private String borncity;
    private String gender;
    private String year; // award year as String
    private String category;
    private String motivation;
    private String name; // affiliation name
    private String city; // affiliation city
    private String country; // affiliation country
    private Integer ageAtAward; // derived/enriched field
    private String normalizedCountryCode; // enriched/standardized code
    private String createdAt; // ISO-8601 timestamp
    private String updatedAt; // ISO-8601 timestamp

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
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (born == null || born.isBlank()) return false;
        return true;
    }
}
