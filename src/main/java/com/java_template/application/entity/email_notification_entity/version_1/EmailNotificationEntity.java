package com.java_template.application.entity.email_notification_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * EmailNotification Entity - Manages email notifications for report delivery to stakeholders
 */
@Data
public class EmailNotificationEntity implements CyodaEntity {
    public static final String ENTITY_NAME = EmailNotificationEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Email notification data
    private String notificationId;
    private String reportId;
    private String recipientEmail;
    private String subject;
    private String body;
    private String attachmentPath;
    private LocalDateTime scheduledTime;
    private LocalDateTime sentTime;
    private Integer deliveryAttempts;
    private String lastError;

    // Email validation pattern (RFC 5322 compliant)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

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
        if (notificationId == null || notificationId.trim().isEmpty()) {
            return false;
        }
        if (reportId == null || reportId.trim().isEmpty()) {
            return false;
        }
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            return false;
        }
        // Validate email format
        if (!EMAIL_PATTERN.matcher(recipientEmail).matches()) {
            return false;
        }
        if (subject == null || subject.trim().isEmpty()) {
            return false;
        }
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        if (scheduledTime == null) {
            return false;
        }
        // Delivery attempts should be non-negative
        if (deliveryAttempts != null && deliveryAttempts < 0) {
            return false;
        }
        // Maximum 3 delivery attempts allowed
        if (deliveryAttempts != null && deliveryAttempts > 3) {
            return false;
        }
        return true;
    }
}
