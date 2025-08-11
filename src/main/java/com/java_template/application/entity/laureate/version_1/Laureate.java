package com.java_template.application.entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;

    private Integer laureateId;
    private String firstname;
    private String surname;
    private String gender;
    private String born;
    private String died;
    private String borncountry;
    private String borncountrycode;
    private String borncity;
    private String year;
    private String category;
    private String motivation;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private Integer ageAtAward;

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
        if (laureateId == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (gender == null || gender.isBlank()) return false;
        if (born == null || born.isBlank()) return false;
        if (borncountry == null || borncountry.isBlank()) return false;
        if (borncountrycode == null || borncountrycode.isBlank()) return false;
        if (borncity == null || borncity.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (motivation == null || motivation.isBlank()) return false;
        if (affiliationName == null || affiliationName.isBlank()) return false;
        if (affiliationCity == null || affiliationCity.isBlank()) return false;
        if (affiliationCountry == null || affiliationCountry.isBlank()) return false;
        return true;
    }
}}
