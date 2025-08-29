package com.java_template.application.entity.consent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Consent implements CyodaEntity {
    public static final String ENTITY_NAME = "Consent"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Required
    private String consent_id;
    private String user_id;
    private String requested_at;
    private String status;
    private String type;

    // Optional
    private String evidence_ref;
    private String granted_at;
    private String revoked_at;
    private String source;

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
        // Validate required string fields using isBlank()
        if (consent_id == null || consent_id.isBlank()) return false;
        if (user_id == null || user_id.isBlank()) return false;
        if (requested_at == null || requested_at.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}