package com.java_template.application.entity.contact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Contact implements CyodaEntity {
    public static final String ENTITY_NAME = "Contact";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String company;
    private String title;
    private String createdAt; // ISO timestamp

    public Contact() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (firstName == null || firstName.isBlank()) return false;
        if (lastName == null || lastName.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        return true;
    }
}
