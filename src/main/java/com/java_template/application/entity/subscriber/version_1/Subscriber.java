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

    // Entity fields based on prototype
    private String id; // technical id (e.g., "sub-42")
    private String name;
    private Boolean active;
    private String createdAt; // ISO-8601 string
    private Contact contact;
    private List<String> subscribedCategories;
    private YearRange subscribedYearRange;

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
        // id and name must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // active must be set
        if (active == null) return false;

        // createdAt must be present
        if (createdAt == null || createdAt.isBlank()) return false;

        // contact with a non-blank email is required
        if (contact == null || !contact.isValid()) return false;

        // subscribedCategories must be present and contain only non-blank strings
        if (subscribedCategories == null || subscribedCategories.isEmpty()) return false;
        for (String cat : subscribedCategories) {
            if (cat == null || cat.isBlank()) return false;
        }

        // subscribedYearRange must be present and valid
        if (subscribedYearRange == null || !subscribedYearRange.isValid()) return false;

        return true;
    }

    @Data
    public static class Contact {
        private String email;

        public Contact() {}

        public boolean isValid() {
            return email != null && !email.isBlank();
        }
    }

    @Data
    public static class YearRange {
        private String from; // e.g., "1900"
        private String to;   // e.g., "1950"

        public YearRange() {}

        public boolean isValid() {
            return from != null && !from.isBlank() && to != null && !to.isBlank();
        }
    }
}