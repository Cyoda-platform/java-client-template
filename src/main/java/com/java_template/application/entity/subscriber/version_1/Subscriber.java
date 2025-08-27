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

    private String id; // technical id (serialized UUID or string)
    private Boolean active;
    private ContactDetails contactDetails;
    private String contactType;
    private String createdAt; // ISO timestamp string
    private Filters filters;
    private String lastNotifiedAt; // ISO timestamp string, nullable
    private Boolean verified;

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
        // Required string fields must not be blank
        if (id == null || id.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Booleans should be non-null
        if (active == null) return false;
        if (verified == null) return false;

        // contactDetails and its URL are required
        if (contactDetails == null) return false;
        if (contactDetails.getUrl() == null || contactDetails.getUrl().isBlank()) return false;

        // Validate filters if present
        if (filters != null) {
            if (filters.getCategory() != null) {
                for (String c : filters.getCategory()) {
                    if (c == null || c.isBlank()) return false;
                }
            }
            if (filters.getCountry() != null) {
                for (String c : filters.getCountry()) {
                    if (c == null || c.isBlank()) return false;
                }
            }
            if (filters.getYearRange() != null) {
                YearRange yr = filters.getYearRange();
                if (yr.getFrom() == null || yr.getFrom().isBlank()) return false;
                if (yr.getTo() == null || yr.getTo().isBlank()) return false;
            }
        }

        return true;
    }

    @Data
    public static class ContactDetails {
        private String url;
        // additional contact fields can be added here
    }

    @Data
    public static class Filters {
        private List<String> category;
        private List<String> country;
        private YearRange yearRange;
    }

    @Data
    public static class YearRange {
        private String from;
        private String to;
    }
}