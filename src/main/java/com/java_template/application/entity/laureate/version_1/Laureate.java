package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate"; 
    public static final Integer ENTITY_VERSION = 1;
    
    private String id; // Serialized UUID for the entity
    private String firstname;
    private String surname;
    private String name;
    private String born;
    private String borncity;
    private String borncountry;
    private String borncountrycode;
    private String category;
    private String city;
    private String country;
    private String died;
    private String gender;
    private Integer year;
    private String motivation;

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
        return !firstname.isBlank() && 
               !surname.isBlank() && 
               !name.isBlank() && 
               !born.isBlank() && 
               !borncity.isBlank() && 
               !borncountry.isBlank() && 
               !borncountrycode.isBlank() && 
               !category.isBlank() && 
               !city.isBlank() && 
               !country.isBlank() && 
               !died.isBlank() && 
               (gender.equals("male") || gender.equals("female")) && 
               year != null && 
               !motivation.isBlank();
    }
}