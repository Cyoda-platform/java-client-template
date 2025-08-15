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

    private Integer id; // domain identifier from OpenDataSoft
    private String firstname; // given name
    private String surname; // family name
    private String born; // ISO date of birth
    private String died; // ISO date of death or null
    private String borncountry; // country name
    private String borncountrycode; // ISO country code
    private String borncity; // city of birth
    private String gender; // gender
    private String year; // award year
    private String category; // award category
    private String motivation; // award motivation
    private String name; // affiliation / organization name
    private String city; // affiliation city
    private String country; // affiliation country
    private Integer calculatedAge; // computed age at award or at death
    private String normalizedCountryCode; // standardized country code after enrichment
    private String rawPayload; // raw JSON response stored for audit

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
        // Required fields: id, year, category and either firstname/surname or name (affiliation)
        if (this.id == null) return false;
        if (this.year == null || this.year.isBlank()) return false;
        if (this.category == null || this.category.isBlank()) return false;
        if ((this.firstname == null || this.firstname.isBlank()) && (this.name == null || this.name.isBlank())) return false;
        // born date if present should not be blank
        if (this.born != null && this.born.isBlank()) return false;
        return true;
    }
}
