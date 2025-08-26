package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private Integer id; // technical id
    private String username;
    private String name;
    private String email;
    private String phone;
    private Address address; // nested address object
    private String processingStatus;
    private String rawPayload; // serialized raw payload (JSON string)
    private String storedReference;
    private String fetchedAt; // ISO timestamp string

    public User() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic required-field validation
        if (id == null || id <= 0) return false;
        if (username == null || username.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // If address is provided, ensure core fields are present
        if (address != null) {
            if (address.getCity() == null || address.getCity().isBlank()) return false;
            if (address.getStreet() == null || address.getStreet().isBlank()) return false;
            // zipcode can be optional, but if provided should not be blank
            if (address.getZipcode() != null && address.getZipcode().isBlank()) return false;
        }

        // fetchedAt, processingStatus, rawPayload, storedReference, phone are optional
        return true;
    }

    @Data
    public static class Address {
        private String street;
        private String city;
        private String zipcode;

        public Address() {}
    }
}