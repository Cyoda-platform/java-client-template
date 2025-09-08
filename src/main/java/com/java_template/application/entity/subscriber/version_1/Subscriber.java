package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Subscriber Entity - Represents users who have subscribed to receive weekly cat facts via email
 * 
 * Entity State Management:
 * - PENDING: Initial state when user signs up
 * - ACTIVE: Confirmed and receiving emails
 * - UNSUBSCRIBED: User has unsubscribed
 * - BOUNCED: Email delivery failed
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field - unique email address
    private String email;
    
    // Optional personal information for personalization
    private String firstName;
    private String lastName;
    
    // Required subscription tracking fields
    private LocalDateTime subscriptionDate;
    private Boolean isActive;
    private Map<String, Object> preferences;
    
    // Email tracking fields
    private LocalDateTime lastEmailSent;
    private Integer totalEmailsReceived;
    
    // Required unsubscribe functionality
    private String unsubscribeToken;

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
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic email format validation
        if (!email.contains("@") || !email.contains(".")) {
            return false;
        }
        
        if (subscriptionDate == null) {
            return false;
        }
        
        // Subscription date cannot be in the future
        if (subscriptionDate.isAfter(LocalDateTime.now())) {
            return false;
        }
        
        if (isActive == null) {
            return false;
        }
        
        if (totalEmailsReceived == null || totalEmailsReceived < 0) {
            return false;
        }
        
        if (unsubscribeToken == null || unsubscribeToken.trim().isEmpty()) {
            return false;
        }
        
        return true;
    }
}
