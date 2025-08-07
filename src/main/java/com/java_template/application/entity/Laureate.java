package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";

    private String laureateId;
    private String firstname;
    private String surname;
    private String gender;
    private String born;
    private String died;
    private String bornCountry;
    private String bornCountryCode;
    private String bornCity;
    private String year;
    private String category;
    private String motivation;
    private String affiliationName;
    private String affiliationCity;
    private String affiliationCountry;
    private String ingestedAt;

    public Laureate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return laureateId != null && !laureateId.isBlank() &&
               firstname != null && !firstname.isBlank() &&
               surname != null && !surname.isBlank() &&
               gender != null && !gender.isBlank() &&
               born != null && !born.isBlank() &&
               bornCountry != null && !bornCountry.isBlank() &&
               bornCountryCode != null && !bornCountryCode.isBlank() &&
               bornCity != null && !bornCity.isBlank() &&
               year != null && !year.isBlank() &&
               category != null && !category.isBlank();
    }
}
