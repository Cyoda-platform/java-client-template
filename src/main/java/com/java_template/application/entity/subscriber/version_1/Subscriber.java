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

    private String id; // domain identifier
    private String technicalId; // datastore-specific identifier returned by POST endpoints
    private String name; // subscriber name or organization
    private String email; // contact email
    private String webhookUrl; // optional webhook endpoint
    private Boolean isActive; // whether subscriber receives notifications
    private String filters; // optional serialization of subscription filters
    private String createdAt; // ISO timestamp
    private String updatedAt; // ISO timestamp

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
        // Basic validation: name required and at least one contact (email or webhookUrl)
        if (this.name == null || this.name.isBlank()) return false;
        if ((this.email == null || this.email.isBlank()) && (this.webhookUrl == null || this.webhookUrl.isBlank())) return false;
        return true;
    }
}
