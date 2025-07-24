package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailNotification implements CyodaEntity {

    private String subscriberEmail; // Email address to notify
    private String notificationDate; // Date of the scores included in the notification, format YYYY-MM-DD
    private String emailSentStatus; // Status of email delivery: PENDING, SENT, FAILED
    private String sentAt; // Timestamp when email was sent

    public EmailNotification() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailNotification");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailNotification");
    }

    @Override
    public boolean isValid() {
        if (subscriberEmail == null || subscriberEmail.isBlank()) return false;
        if (notificationDate == null || notificationDate.isBlank()) return false;
        if (emailSentStatus == null || emailSentStatus.isBlank()) return false;
        return true;
    }
}
