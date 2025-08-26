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

    // Technical/Business id (serialized UUID or string id)
    private String subscriberId;

    // Human friendly name
    private String name;

    // Whether the subscriber is active
    private Boolean active;

    // Contact details (e.g., webhook URL)
    private ContactDetails contactDetails;

    // Type of contact (e.g., "webhook")
    private String contactType;

    // Delivery mode (e.g., "summary", "full")
    private String deliveryMode;

    // Filters applied by the subscriber
    private Filters filters;

    // Retry policy for delivery attempts
    private RetryPolicy retryPolicy;

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
        // Validate required string fields using isBlank()
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (deliveryMode == null || deliveryMode.isBlank()) return false;

        // active must be present
        if (active == null) return false;

        // contactDetails and its url must be present and non-blank
        if (contactDetails == null) return false;
        if (contactDetails.getUrl() == null || contactDetails.getUrl().isBlank()) return false;

        // If retryPolicy is present, validate numeric constraints
        if (retryPolicy != null) {
            if (retryPolicy.getBackoffSeconds() == null || retryPolicy.getBackoffSeconds() < 0) return false;
            if (retryPolicy.getMaxAttempts() == null || retryPolicy.getMaxAttempts() < 0) return false;
        }

        // Filters are optional; if present, ensure lists are non-null (they may be empty)
        if (filters != null) {
            // lists can be null in input; accept null but if present ensure elements are non-blank
            if (filters.getBorncountry() != null) {
                for (String v : filters.getBorncountry()) {
                    if (v == null || v.isBlank()) return false;
                }
            }
            if (filters.getCategories() != null) {
                for (String v : filters.getCategories()) {
                    if (v == null || v.isBlank()) return false;
                }
            }
            if (filters.getYears() != null) {
                for (String v : filters.getYears()) {
                    if (v == null || v.isBlank()) return false;
                }
            }
        }

        return true;
    }

    @Data
    public static class ContactDetails {
        private String url;
    }

    @Data
    public static class Filters {
        private List<String> borncountry;
        private List<String> categories;
        private List<String> years;
    }

    @Data
    public static class RetryPolicy {
        private Integer backoffSeconds;
        private Integer maxAttempts;
    }
}