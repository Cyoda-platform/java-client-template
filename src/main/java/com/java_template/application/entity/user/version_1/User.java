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
    private String id; // technical id (serialized UUID)
    private String email;
    private String name;
    private String phone;
    private Address primaryAddress;
    private String profileUpdatedAt; // ISO-8601 timestamp

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
        // Basic required fields validation
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // If primaryAddress is provided, validate its essential fields
        if (primaryAddress != null) {
            if (primaryAddress.getLine1() == null || primaryAddress.getLine1().isBlank()) return false;
            if (primaryAddress.getCity() == null || primaryAddress.getCity().isBlank()) return false;
            if (primaryAddress.getCountry() == null || primaryAddress.getCountry().isBlank()) return false;
            if (primaryAddress.getPostal() == null || primaryAddress.getPostal().isBlank()) return false;
        }

        return true;
    }

    @Data
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String postal;
        private String country;

        public Address() {}
    }
}