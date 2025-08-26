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
    private String id;
    private String name;
    private String address;
    // Serialized UUIDs / foreign keys as Strings
    private List<String> adoptedPetIds;
    private ContactInfo contactInfo;
    private List<String> favorites;
    private List<String> profileBadges;
    private String verificationStatus;

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
        // Required basic fields
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (verificationStatus == null || verificationStatus.isBlank()) return false;

        // Contact info should be present and valid
        if (contactInfo == null || !contactInfo.isValid()) return false;

        // If lists are present, ensure they don't contain blank entries
        if (adoptedPetIds != null) {
            for (String pid : adoptedPetIds) {
                if (pid == null || pid.isBlank()) return false;
            }
        }
        if (favorites != null) {
            for (String f : favorites) {
                if (f == null || f.isBlank()) return false;
            }
        }
        if (profileBadges != null) {
            for (String b : profileBadges) {
                if (b == null || b.isBlank()) return false;
            }
        }

        return true;
    }

    @Data
    public static class ContactInfo {
        private String email;
        private String phone;

        public boolean isValid() {
            if (email == null || email.isBlank()) return false;
            // phone may be optional, but if present should not be blank
            if (phone != null && phone.isBlank()) return false;
            return true;
        }
    }
}