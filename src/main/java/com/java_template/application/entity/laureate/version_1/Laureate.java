package com.java_template.application.entity.laureate.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer id; // business id from OpenDataSoft record id field
    private String firstname; // laureate given name
    private String surname; // laureate family name
    private String gender; // gender from source
    private String born; // ISO-8601 date of birth
    private String died; // ISO-8601 date of death or null
    private String borncountry; // country name of birth
    private String borncountrycode; // country code of birth
    private String borncity; // birth city
    private String year; // award year
    private String category; // award category, e.g., Chemistry
    private String motivation; // motivation text
    private String affiliationName; // affiliation name from source, mapped from name
    private String affiliationCity; // affiliation city
    private String affiliationCountry; // affiliation country
    private Integer ageAtAward; // enrichment: calculated age at award if born/year available
    private String normalizedCountryCode; // enrichment: standardized country code
    private Boolean dataValidated; // true if Validation Processor passed
    private Boolean dataEnriched; // true if Enrichment Processor succeeded
    private String sourceJobTechnicalId; // technicalId of Job that created/updated this laureate

    // New fields referenced by processors
    private String persistedAt; // ISO-8601 datetime when laureate was persisted
    private String validationErrors; // validation error details
    private String enrichmentErrors; // enrichment error details

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
        // id is required and should be positive
        if (this.id == null || this.id <= 0) return false;
        // at least one of firstname or surname must be present
        if ((this.firstname == null || this.firstname.isBlank()) && (this.surname == null || this.surname.isBlank())) return false;
        // year is required
        if (this.year == null || this.year.isBlank()) return false;
        return true;
    }
}
