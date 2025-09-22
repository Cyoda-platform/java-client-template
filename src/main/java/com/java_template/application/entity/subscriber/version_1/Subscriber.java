package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Subscriber Entity
 * 
 * Represents an email subscriber who receives analysis reports. 
 * Manages subscription preferences and delivery status.
 */
@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // Required business identifier field
    private String subscriberId;
    
    // Required core business fields
    private String email;
    
    // Optional fields for additional business data
    private String name;
    private LocalDateTime subscribedAt;
    private LocalDateTime lastEmailSent;
    private EmailPreferences emailPreferences;
    private Boolean isActive;

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
        return subscriberId != null && 
               email != null && 
               isValidEmail(email);
    }
    
    /**
     * Validates if the email address is in valid format
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Nested class for email preferences
     * Use this pattern for grouped related fields
     */
    @Data
    public static class EmailPreferences {
        private String frequency; // IMMEDIATE, DAILY, WEEKLY
        private String format;    // HTML, TEXT
        private List<String> topics; // Subscribed topics or data source types
    }
}
