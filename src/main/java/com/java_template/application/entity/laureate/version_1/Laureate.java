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
    private Integer age;
    private String born; // ISO date string e.g., "1930-09-12"
    private String died; // ISO date string or null
    private String bornCity;
    private String bornCountry;
    private String bornCountryCode;
    private String category;
    private String motivation;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private String normalizedCountryCode;
    private String gender;
    private String year;
    private String createdAt; // ISO timestamp string e.g., "2025-08-26T10:00:05Z"
    private String sourceJobId; // foreign key reference as serialized UUID/string

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
        // Validate required fields
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (sourceJobId == null || sourceJobId.isBlank()) return false;
        return true;
    }
}