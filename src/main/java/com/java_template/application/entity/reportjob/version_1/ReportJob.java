package com.java_template.application.entity.reportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ReportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ReportJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String completedAt;
    private Filters filters;
    private Boolean includeCharts;
    private String name;
    private String requestedAt;
    private String requestedBy;
    private String status;

    public ReportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields
        if (name == null || name.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If filters provided, validate its essential fields
        if (filters != null) {
            if (filters.getDateFrom() == null || filters.getDateFrom().isBlank()) return false;
            if (filters.getDateTo() == null || filters.getDateTo().isBlank()) return false;
            if (filters.getMinPrice() == null) return false;
            if (filters.getMaxPrice() == null) return false;
        }

        return true;
    }

    @Data
    public static class Filters {
        private String dateFrom;
        private String dateTo;
        private Boolean depositPaid; // can be null to indicate "any"
        private Integer maxPrice;
        private Integer minPrice;
    }
}