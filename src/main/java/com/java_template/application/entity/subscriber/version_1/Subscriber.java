package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Subscriber Entity - Represents a user subscribed to weekly cat facts
 * 
 * This entity manages subscriber information including:
 * - Email address (business identifier)
 * - Subscription status (active/unsubscribed)
 * - Subscription and unsubscription timestamps
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - email address
    private String email;

    // Core business fields
    private String status; // "active" or "unsubscribed"
    private LocalDateTime subscribedAt;
    private LocalDateTime unsubscribedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return email != null && !email.isBlank() && status != null;
    }
}

