package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID
    private String name;
    private String accountStatus;
    private String address;
    private List<String> adoptedPets; // list of serialized pet UUIDs
    private Contact contact;
    private String createdAt; // ISO timestamp
    private List<String> favorites; // list of serialized pet UUIDs

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
        // id and name are required
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        // contact should exist and provide at least one contact method (email or phone)
        if (contact == null) return false;
        boolean emailProvided = contact.getEmail() != null && !contact.getEmail().isBlank();
        boolean phoneProvided = contact.getPhone() != null && !contact.getPhone().isBlank();
        if (!emailProvided && !phoneProvided) return false;
        return true;
    }

    @Data
    public static class Contact {
        private String email;
        private String phone;
    }
}