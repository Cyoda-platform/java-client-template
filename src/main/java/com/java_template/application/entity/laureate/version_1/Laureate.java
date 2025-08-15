package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer id; // source laureate id from OpenDataSoft
    private String firstname;
    private String surname;
    private String name; // affiliation or full name where applicable
    private String born; // date of birth ISO-8601 or null
    private String died; // date of death ISO-8601 or null
    private String gender; // male/female/other
    private String borncountry;
    private String borncountrycode; // ISO country code
    private String borncity;
    private String year; // award year
    private String category; // award category
    private String motivation; // award motivation text
    private String city; // affiliation city
    private String country; // affiliation country
    private Integer ageAtAward; // enriched field
    private String normalizedCountryCode; // enriched
    private String sourceFetchedAt; // ISO-8601 timestamp when data pulled
    private String status; // RECEIVED, VALIDATED, ...
    private Integer duplicateOf; // id of existing laureate record if deduplicated/merged
    private Map<String,String> validations; // validation messages/warnings
    private String lastError; // last error message if processing failed

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
        // Basic validation according to requirements
        if (this.id == null) return false;
        if (this.firstname == null || this.firstname.isBlank()) return false;
        if (this.surname == null || this.surname.isBlank()) return false;
        if (this.year == null || this.year.isBlank()) return false;
        if (this.category == null || this.category.isBlank()) return false;
        return true;
    }
}
