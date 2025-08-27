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
    private String motivation;
    private String category;
    private String year;
    private String born;
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private Integer derivedAgeAtAward;
    private String died;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private String persistedAt;
    private String recordStatus;
    private String gender;

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
        // Basic required-field validation
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (persistedAt == null || persistedAt.isBlank()) return false;
        if (recordStatus == null || recordStatus.isBlank()) return false;
        return true;
    }
}