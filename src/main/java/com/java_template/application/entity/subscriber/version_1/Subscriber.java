package com.java_template.application.entity.subscriber.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

import static com.java_template.common.config.Config.ENTITY_VERSION;

/**
 * Represents a user who has subscribed to receive weekly cat fact emails.
 * Manages subscription lifecycle through workflow states.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscriber implements CyodaEntity {

    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Unique identifier for the subscriber
     */
    private Long id;

    /**
     * Email address of the subscriber (required, unique)
     */
    private String email;

    /**
     * First name of the subscriber (optional)
     */
    private String firstName;

    /**
     * Last name of the subscriber (optional)
     */
    private String lastName;

    /**
     * Date and time when the user subscribed
     */
    private LocalDateTime subscriptionDate;

    /**
     * Whether the subscription is active (default: true)
     */
    private Boolean isActive;

    /**
     * Unique token for unsubscribing (UUID)
     */
    private String unsubscribeToken;

    /**
     * Email preferences (frequency, format, etc.)
     */
    private Map<String, Object> preferences;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        // Basic validation - email is required
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Email format validation (basic)
        if (!email.contains("@") || !email.contains(".")) {
            return false;
        }
        
        // Subscription date should be set
        if (subscriptionDate == null) {
            return false;
        }
        
        // isActive should be set
        if (isActive == null) {
            return false;
        }
        
        return true;
    }
}
