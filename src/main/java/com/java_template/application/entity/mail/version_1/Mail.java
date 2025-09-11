package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Mail Entity - Represents an email message that can be either happy or gloomy
 * 
 * This entity contains information about the email's mood and the list of recipients.
 * The isHappy field determines the content type of the mail, while the entity state
 * (accessible via entity.meta.state) tracks the workflow progression.
 */
@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = Mail.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    /**
     * Indicates whether the mail content is happy (true) or gloomy (false)
     * Required field - must not be null
     */
    private Boolean isHappy;

    /**
     * List of email addresses to send the mail to
     * Required field - must not be null, not empty, and contain valid email addresses
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

        // Validate each email address in the list
        for (String email : mailList) {
            if (email == null || email.trim().isEmpty() || !isValidEmail(email)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates if an email address is in valid format
     * @param email the email address to validate
     * @return true if the email is valid, false otherwise
     */
    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
