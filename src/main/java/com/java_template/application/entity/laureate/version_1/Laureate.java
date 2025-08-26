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
    private String id;
    private String fullName;
    private String category;
    private String year;
    private String motivation;
    private String status;
    private String sourceRecordId;
    private String birthCity;
    private String birthCountry;
    private String birthDate;
    private String diedDate;
    private String createdAt;

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
        // Required fields: id, fullName, category, year, status, createdAt
        if (id == null || id.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}