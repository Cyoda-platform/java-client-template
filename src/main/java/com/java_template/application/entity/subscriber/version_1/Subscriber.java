package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String subscriberId;
    private String name;
    private String contactEndpoint;
    private String createdAt; // ISO-8601 timestamp as String
    private String format; // e.g., "summary"
    private String status; // e.g., "ACTIVE" (use String for enums)
    private Filters filters;

    public Subscriber() {} 

    @Data
    public static class Filters {
        private String category;
        private String country;
        private Integer prizeYear;
        public Filters() {}
    }

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
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactEndpoint == null || contactEndpoint.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If filters are provided, at least one filter field should be set and valid
        if (filters != null) {
            boolean hasCategory = filters.getCategory() != null && !filters.getCategory().isBlank();
            boolean hasCountry = filters.getCountry() != null && !filters.getCountry().isBlank();
            boolean hasPrizeYear = filters.getPrizeYear() != null;
            if (!hasCategory && !hasCountry && !hasPrizeYear) return false;
        }

        return true;
    }
}