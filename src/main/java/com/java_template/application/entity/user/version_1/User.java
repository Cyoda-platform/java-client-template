package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String userId; // serialized UUID
    private String name;
    private String email;
    private String phone;
    private Address address;

    public User() {} 

    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;
        private String updatedAt;
        public Address() {}
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate basic required string fields
        if (userId == null || userId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // phone may be optional but if provided should not be blank
        if (phone != null && phone.isBlank()) return false;
        // address must be present and its key fields should be non-blank
        if (address == null) return false;
        if (address.getLine1() == null || address.getLine1().isBlank()) return false;
        if (address.getCity() == null || address.getCity().isBlank()) return false;
        if (address.getPostcode() == null || address.getPostcode().isBlank()) return false;
        if (address.getCountry() == null || address.getCountry().isBlank()) return false;
        // updatedAt in address is optional but if present should not be blank
        if (address.getUpdatedAt() != null && address.getUpdatedAt().isBlank()) return false;
        return true;
    }
}