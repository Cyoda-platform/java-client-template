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

    private String addressId;
    private String userId;
    private String line1;
    private String line2;
    private String city;
    private String region;
    private String postalCode;
    private String country;
    private String type; // shipping or billing
    private Boolean primary;

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
        if (addressId == null || addressId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (line1 == null || line1.isBlank()) return false;
        if (city == null || city.isBlank()) return false;
        if (postalCode == null || postalCode.isBlank()) return false;
        if (country == null || country.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}