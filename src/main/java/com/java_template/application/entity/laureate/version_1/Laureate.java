package com.java_template.application.entity.laureate.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId;
    private Integer externalId;
    private String firstname;
    private String surname;
    private String born;
    private String died;
    private String borncountry;
    private String borncountrycode;
    private String borncity;
    private String gender;
    private String year;
    private String category;
    private String motivation;
    private String affiliation_name;
    private String affiliation_city;
    private String affiliation_country;
    private Integer calculatedAgeAtAward;
    private String normalizedCountryCode;
    private Boolean detectedDuplicates;
    private List<String> validationErrors;
    private String sourceJobTechnicalId;
    private Object rawPayload;
    private String persistedAt;
    private Boolean published;
    private String createdAt;
    private String updatedAt;

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
        if (externalId == null) return false;
        boolean hasName = (firstname != null && !firstname.isBlank()) || (surname != null && !surname.isBlank());
        if (!hasName) return false;
        if (year == null || year.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        boolean hasBornInfo = (borncountry != null && !borncountry.isBlank()) || (borncity != null && !borncity.isBlank());
        if (!hasBornInfo) return false;
        return true;
    }
}
