package com.java_template.application.entity.address.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Address implements CyodaEntity {
    public static final String ENTITY_NAME = "Address"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID or technical id
    private String userId; // reference to User (serialized UUID)
    private String line1;
    private String line2;
    private String city;
    private String postalCode;
    private String country;
    private String status;
    private Boolean isDefault;
    private Boolean verified;
    private String region; // added region field used by controller

    public Address() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields: use isBlank checks (guarding against null)
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (line1 == null || line1.isBlank()) return false;
        if (city == null || city.isBlank()) return false;
        if (country == null || country.isBlank()) return false;
        if (postalCode == null || postalCode.isBlank()) return false;
        // status can be optional in some flows, but if present must not be blank
        if (status != null && status.isBlank()) return false;
        // Booleans are optional; if provided, accept their values
        return true;
    }
}