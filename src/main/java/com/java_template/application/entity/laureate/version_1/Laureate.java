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
    private String category;
    private String motivation;
    private String year;
    private String born;
    private String died;
    private String borncity;
    private String borncountry;
    private String borncountrycode;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private Integer computedAge;
    // foreign key reference to ingest job (serialized UUID / technical id)
    private String ingestJobId;

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
        // Basic required fields validation
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        // ingestJobId is a serialized reference; ensure it's present and not blank
        if (ingestJobId == null || ingestJobId.isBlank()) return false;
        // computedAge if provided should be non-negative
        if (computedAge != null && computedAge < 0) return false;
        return true;
    }
}