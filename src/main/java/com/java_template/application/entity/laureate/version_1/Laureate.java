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
    private String affiliationCity;
    private String affiliationCountry;
    private String affiliationName;
    private Integer ageAtAward;
    private String born;
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private String category;
    private String died;
    private String firstname;
    private String gender;
    private Integer id;
    private String motivation;
    private String normalizedCountryCode;
    private String sourceJobId; // foreign key reference to Job (serialized id)
    private String surname;
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
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (sourceJobId == null || sourceJobId.isBlank()) return false;
        return true;
    }
}