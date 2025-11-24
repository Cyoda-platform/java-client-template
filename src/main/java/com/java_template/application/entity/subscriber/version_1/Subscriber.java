package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Subscriber entity represents a user who has signed up for the weekly cat fact subscription.
 * Manages subscription status, email, and subscription preferences.
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String subscriberId;

    // Required core business fields
    private String email;
    private LocalDateTime subscriptionDate;
    private Boolean isActive;

    // Optional fields for additional business data
    private String firstName;
    private String lastName;
    private String preferences;
    private LocalDateTime lastEmailSentDate;
    private Integer emailsReceived;

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
        return subscriberId != null && !subscriberId.trim().isEmpty() &&
               email != null && !email.trim().isEmpty();
    }
}

