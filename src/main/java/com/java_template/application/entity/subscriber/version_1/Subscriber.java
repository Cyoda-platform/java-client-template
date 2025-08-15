package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Subscriber fields
    private String email; // subscriber email address
    private String subscribedAt; // ISO-8601 timestamp when subscription was created
    private String status; // active, unsubscribed, pending
    private String preferences; // JSON serialized as String
    private Boolean confirmed; // whether subscription confirmation completed
    private String lastNotificationAt; // ISO-8601 timestamp of last notification sent

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
        // email is required and must be non-blank
        return email != null && !email.isBlank();
    }
}
