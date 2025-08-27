package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String subscriberId;
    private String name;
    private Boolean active;
    private ContactMethods contactMethods;
    private Interests interests;
    private String lastNotificationStatus;
    private String lastNotifiedJobId; // foreign key reference as serialized UUID/string
    private String preferredPayload;

    public Subscriber() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // subscriberId and name must be present and not blank
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // contactMethods must be present and have at least one contact method (email or webhookUrl)
        if (contactMethods == null) return false;
        boolean hasEmail = contactMethods.getEmail() != null && !contactMethods.getEmail().isBlank();
        boolean hasWebhook = contactMethods.getWebhookUrl() != null && !contactMethods.getWebhookUrl().isBlank();
        if (!hasEmail && !hasWebhook) return false;

        // If lastNotifiedJobId is present it must not be blank (foreign key as string)
        if (lastNotifiedJobId != null && lastNotifiedJobId.isBlank()) return false;

        // Optional string fields, if present, must not be blank
        if (preferredPayload != null && preferredPayload.isBlank()) return false;
        if (lastNotificationStatus != null && lastNotificationStatus.isBlank()) return false;

        return true;
    }

    @Data
    public static class ContactMethods {
        private String email;
        private String webhookUrl;
    }

    @Data
    public static class Interests {
        private List<String> categories;
        private List<String> countries;
        private List<String> years;
    }
}