package com.java_template.application.entity.consent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Consent implements CyodaEntity {
    public static final String ENTITY_NAME = "Consent"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on requirements prototype
    private String consentId;    // technical id
    private String evidenceRef;  // reference to evidence entity (serialized UUID)
    private String grantedAt;    // ISO-8601 timestamp string
    private String requestedAt;  // ISO-8601 timestamp string
    private String revokedAt;    // ISO-8601 timestamp string (may be blank if not revoked)
    private String source;       // e.g., signup_form
    private String status;       // e.g., active, revoked
    private String type;         // e.g., marketing
    private String userId;       // reference to user (serialized UUID)

    public Consent() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: consentId, userId, type, status, requestedAt
        if (consentId == null || consentId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        return true;
    }
}