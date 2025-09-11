package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * Mail Entity - Represents an email message that can be either happy or gloomy
 * 
 * This entity contains information about the email's mood and the list of recipients.
 * The entity state is managed automatically by the workflow system and represents 
 * the current stage of the mail processing lifecycle.
 */
@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = Mail.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Indicates whether the mail content is happy (true) or gloomy (false)
     * Required field that determines the content type and processing logic
     */
    private Boolean isHappy;
    
    /**
     * List of email addresses to send the mail to
     * Required field that must not be null, empty, and contain valid email formats
     */
    private List<String> mailList;

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
        if (isHappy == null) {
            return false;
        }
        
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        
        // Validate email format for each address
        for (String email : mailList) {
            if (email == null || email.trim().isEmpty() || !isValidEmail(email)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Simple email validation
     * Checks for basic email format: contains @ and has text before and after
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String trimmedEmail = email.trim();
        int atIndex = trimmedEmail.indexOf('@');
        
        // Must contain @ and have text before and after it
        return atIndex > 0 && atIndex < trimmedEmail.length() - 1 && 
               trimmedEmail.indexOf('@', atIndex + 1) == -1; // Only one @
    }
}
