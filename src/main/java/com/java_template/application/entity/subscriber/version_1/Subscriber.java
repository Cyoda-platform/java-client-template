package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String email;               // recipient address
    private String name;                // optional recipient name
    private String subscription_status; // active, unsubscribed
    private String preferred_format;    // html, pdf, both
    private String created_at;          // when subscriber was created
    private String technicalId;         // datastore id returned by POST

    public Subscriber() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // email and preferred_format are required
        if (email == null || email.isBlank()) return false;
        if (preferred_format == null || preferred_format.isBlank()) return false;
        return true;
    }
}
