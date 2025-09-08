package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Subscriber Entity - Represents a user who has subscribed to receive weekly cat facts via email
 * 
 * Entity States (managed by workflow):
 * - PENDING: Initial state when subscription is created but not yet confirmed
 * - ACTIVE: Subscription is confirmed and active
 * - INACTIVE: Subscription is temporarily deactivated
 * - UNSUBSCRIBED: User has permanently unsubscribed
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;
    
    // Required core business fields
    private String email;
    
    // Optional fields for additional business data
    private String firstName;
    private String lastName;
    private LocalDateTime subscriptionDate;
    private Boolean isActive;
    private Map<String, Object> preferences;

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
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return false;
        }
        
        // Validate subscription date is not in the future
        if (subscriptionDate != null && subscriptionDate.isAfter(LocalDateTime.now())) {
            return false;
        }
        
        return true;
    }
}
