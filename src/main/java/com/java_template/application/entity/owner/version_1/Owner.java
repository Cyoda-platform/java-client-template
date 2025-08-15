package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String name; // full name
    private String contactEmail; // primary contact
    private String contactPhone; // phone
    private String address; // postal address
    private String role; // customer/admin/staff
    private List<String> favorites = new ArrayList<>(); // pet ids
    private List<String> adoptionHistory = new ArrayList<>(); // adoptionRequest ids

    public Owner() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;
        return true;
    }
}