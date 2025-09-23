package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Subscriber Entity - Manages user subscriptions for weekly cat fact emails
 * 
 * This entity represents users who have subscribed to receive weekly cat fact emails.
 * It includes subscription management, preferences, and tracking information.
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String subscriberId;
    
    // Required core business fields
    private String email;
    private String name;
    private LocalDateTime subscriptionDate;
    private Boolean isActive;

    // Optional fields for additional business data
    private SubscriberPreferences preferences;
    private LocalDateTime lastEmailSent;
    private Integer totalEmailsReceived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return subscriberId != null && !subscriberId.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               subscriptionDate != null &&
               isActive != null;
    }

    /**
     * Nested class for subscriber preferences
     * Allows users to customize their subscription experience
     */
    @Data
    public static class SubscriberPreferences {
        private String preferredTimeZone;
        private String emailFormat; // HTML, TEXT
        private Boolean receiveWeeklyDigest;
        private Boolean receiveSpecialOffers;
        private String frequency; // WEEKLY, DAILY (future enhancement)
    }
}
