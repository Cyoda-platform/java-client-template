package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Boolean active;
    private String contactAddress;
    private String contactType; // use String for enum-like values (e.g., "webhook")
    private Filters filters;
    private String id;
    private String name;
    private String preferredPayload; // use String for enum-like values (e.g., "summary")
    private RetryPolicy retryPolicy;
    private String technicalId;

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
        // required basic fields
        if (active == null) return false;
        if (contactAddress == null || contactAddress.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (technicalId == null || technicalId.isBlank()) return false;
        // optional id may be blank or null depending on creation scenario

        // validate preferredPayload if provided
        if (preferredPayload != null && preferredPayload.isBlank()) return false;

        // validate filters if present
        if (filters != null) {
            if (filters.getCategories() == null || filters.getCategories().isEmpty()) return false;
            for (String c : filters.getCategories()) {
                if (c == null || c.isBlank()) return false;
            }
        }

        // validate retryPolicy if present
        if (retryPolicy != null) {
            if (retryPolicy.getBackoffSeconds() == null || retryPolicy.getBackoffSeconds() < 0) return false;
            if (retryPolicy.getMaxAttempts() == null || retryPolicy.getMaxAttempts() < 1) return false;
        }

        return true;
    }

    @Data
    public static class Filters {
        private List<String> categories;
    }

    @Data
    public static class RetryPolicy {
        private Integer backoffSeconds;
        private Integer maxAttempts;
    }
}