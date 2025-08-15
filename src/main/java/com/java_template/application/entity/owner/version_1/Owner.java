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

    private String ownerId; // external owner id from source systems; business identifier
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String source; // data source identifier, e.g., Petstore API
    private List<String> petExternalIds; // list of external petIds associated in source
    private List<String> petTechnicalIds; // list of persisted Pet technicalIds associated, nullable (serialized UUIDs as Strings)
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601

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
        // Basic validation: ownerId and email required; at least one of firstName/lastName should be present
        if(this.ownerId == null || this.ownerId.isBlank()) return false;
        if(this.email == null || this.email.isBlank()) return false;
        boolean hasName = (this.firstName != null && !this.firstName.isBlank()) || (this.lastName != null && !this.lastName.isBlank());
        if(!hasName) return false;
        return true;
    }
}
