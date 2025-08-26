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
    private String technicalId;
    private String sourceJobTechnicalId; // foreign key reference as serialized technical id
    private String name;
    private String firstname;
    private String surname;
    private String motivation;
    private String category;
    private String year;
    private Integer ageAtAward;
    private String born; // date as ISO string
    private String died; // date as ISO string or null
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private String gender;
    private String validationStatus;
    private Affiliation affiliation;

    @Data
    public static class Affiliation {
        private String name;
        private String city;
        private String country;
    }

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
        // technicalId must be present
        if (technicalId == null || technicalId.isBlank()) return false;
        // id must be present
        if (id == null) return false;
        // must have either full name or firstname+surname
        boolean hasFullName = name != null && !name.isBlank();
        boolean hasParts = firstname != null && !firstname.isBlank() && surname != null && !surname.isBlank();
        if (!hasFullName && !hasParts) return false;
        // category and year are required
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        return true;
    }
}