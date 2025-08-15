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
    private String id; // domain identifier
    private String name;
    private ContactMethods contactMethods;
    private Boolean active;
    private Filters filters;
    private String createdAt; // ISO-8601 timestamp
    private String updatedAt; // ISO-8601 timestamp

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
        if (name == null || name.isBlank()) return false;
        if (contactMethods == null) return false;
        boolean hasEmail = contactMethods.getEmail() != null && !contactMethods.getEmail().isBlank();
        boolean hasWebhook = contactMethods.getWebhookUrl() != null && !contactMethods.getWebhookUrl().isBlank();
        if (!hasEmail && !hasWebhook) return false;
        return true;
    }

    @Data
    public static class ContactMethods {
        private String email;
        private String webhookUrl;
    }

    @Data
    public static class Filters {
        private List<String> categories;
        private Years years;
        private List<String> borncountry;
        private List<String> affiliationCountry;
    }

    @Data
    public static class Years {
        private String from;
        private String to;
    }
}
