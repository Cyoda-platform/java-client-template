package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;

    private String laureateId;
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
    private String name;
    private String city;
    private String country;

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
        if (laureateId == null || laureateId.isBlank()) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (born == null || born.isBlank()) return false;
        // died can be null or blank
        if (borncountry == null || borncountry.isBlank()) return false;
        if (borncountrycode == null || borncountrycode.isBlank()) return false;
        if (borncity == null || borncity.isBlank()) return false;
        if (gender == null || gender.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (motivation == null || motivation.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (city == null || city.isBlank()) return false;
        if (country == null || country.isBlank()) return false;
        return true;
    }
}
