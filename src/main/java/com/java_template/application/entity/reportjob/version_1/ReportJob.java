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
    private String jobId;
    private String requestedAt; // ISO-8601 timestamp
    private String requestedBy;
    private String resultReportId; // foreign key reference (serialized UUID/string)
    private String schedule;
    private String status;
    private String visualization;
    private FilterCriteria filterCriteria;

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
        if (jobId == null || jobId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (requestedBy == null || requestedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        if (filterCriteria != null) {
            // validate date range if present
            if (filterCriteria.getDateRange() != null) {
                DateRange dr = filterCriteria.getDateRange();
                if (dr.getFrom() == null || dr.getFrom().isBlank()) return false;
                if (dr.getTo() == null || dr.getTo().isBlank()) return false;
            }
            // validate price range if both provided
            Double min = filterCriteria.getMinPrice();
            Double max = filterCriteria.getMaxPrice();
            if (min != null && max != null && min > max) return false;
        }

        return true;
    }

    @Data
    public static class FilterCriteria {
        private String customerName;
        private DateRange dateRange;
        private String depositStatus;
        private Double maxPrice;
        private Double minPrice;
    }

    @Data
    public static class DateRange {
        private String from;
        private String to;
    }
}