package com.java_template.application.entity.emaildispatch.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class EmailDispatch implements CyodaEntity {
    public static final String ENTITY_NAME = "EmailDispatch";
    public static final Integer ENTITY_VERSION = 1;

    private String subscriberEmail; // email address of the subscriber receiving the email
    private String catFact; // the cat fact content sent
    private String dispatchedAt; // ISO8601 datetime string when the email was sent

    public EmailDispatch() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (subscriberEmail == null || subscriberEmail.isBlank()) return false;
        if (catFact == null || catFact.isBlank()) return false;
        if (dispatchedAt == null || dispatchedAt.isBlank()) return false;
        return true;
    }
}
