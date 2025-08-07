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
    private Integer id;
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
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (laureateId == null || laureateId.isBlank()) return false;
        if (id == null) return false;
        if (firstname == null || firstname.isBlank()) return false;
        if (surname == null || surname.isBlank()) return false;
        if (year == null || year.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        return true;
    }
}
