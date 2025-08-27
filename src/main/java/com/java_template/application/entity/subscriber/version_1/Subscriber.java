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
    private String subscriberId; // external/business id
    private Boolean active;
    private ContactDetails contactDetails;
    private String contactType;
    private Filters filters;
    private String lastNotifiedAt; // ISO-8601 timestamp
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
        // subscriberId must be present
        if (subscriberId == null || subscriberId.isBlank()) return false;
        // active must be provided
        if (active == null) return false;
        // contactType must be present
        if (contactType == null || contactType.isBlank()) return false;
        // preferredPayload must be present
        if (preferredPayload == null || preferredPayload.isBlank()) return false;
        // contactDetails and its url must be present
        if (contactDetails == null) return false;
        if (contactDetails.getUrl() == null || contactDetails.getUrl().isBlank()) return false;
        // filters optional; if present, inner lists should not be null (but may be empty)
        if (filters != null) {
            if (filters.getCategories() == null) return false;
            if (filters.getYears() == null) return false;
        }
        return true;
    }

    @Data
    public static class ContactDetails {
        private String url;
        // other contact fields can be added here (e.g., email, phone)
    }

    @Data
    public static class Filters {
        private List<String> categories;
        private List<String> years;
    }
}