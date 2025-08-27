package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;

@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer";

    private String customerId;
    private String name;
    private String email;
    private String phone;
    private List<Address> addresses;
    private String createdAt;
    private String updatedAt;

    @Data
    public static class Address {
        private String addressId;
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }

    public Customer() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (customerId == null || customerId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (addresses == null || addresses.isEmpty()) return false;
        for (Address addr : addresses) {
            if (addr.getLine1() == null || addr.getLine1().isBlank()) return false;
            if (addr.getCity() == null || addr.getCity().isBlank()) return false;
            if (addr.getPostcode() == null || addr.getPostcode().isBlank()) return false;
            if (addr.getCountry() == null || addr.getCountry().isBlank()) return false;
        }
        return true;
    }
}
