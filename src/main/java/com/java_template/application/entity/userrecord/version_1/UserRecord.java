package com.java_template.application.entity.userrecord.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class UserRecord implements CyodaEntity {
    public static final String ENTITY_NAME = "UserRecord";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId;
    private Integer externalId; // id from Fakerest
    private String firstName;
    private String lastName;
    private String email;
    private String sourcePayload; // raw JSON fetched
    private String transformedAt; // ISO datetime
    private Boolean normalized; // true if standardization applied
    private String storedAt; // ISO datetime
    private String lastSeen; // ISO datetime

    public UserRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (externalId == null) return false;
        if (email == null || email.isBlank()) return false;
        if (sourcePayload == null || sourcePayload.isBlank()) return false;
        return true;
    }
}
