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

    private String technicalId; // internal id assigned by system
    private String jobName; // human name for job
    private String initiatedBy; // user or system
    private String filterDateFrom; // ISO date, optional
    private String filterDateTo; // ISO date, optional
    private Double minPrice; // optional
    private Double maxPrice; // optional
    private Boolean depositPaid; // optional
    private String grouping; // daily weekly monthly, optional
    private String presentationType; // table chart, optional
    private String status; // PENDING IN_PROGRESS COMPLETED FAILED
    private String createdAt; // ISO datetime
    private String completedAt; // ISO datetime, optional

    // Optional fields used by processors
    private String reportId; // id of generated report
    private String errorDetails; // any error details

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
        // Required string fields must be non-null and not blank
        if (jobName == null || jobName.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Optional date fields if provided must not be blank
        if (filterDateFrom != null && filterDateFrom.isBlank()) return false;
        if (filterDateTo != null && filterDateTo.isBlank()) return false;

        // If both prices provided, ensure bounds are sane
        if (minPrice != null && maxPrice != null) {
            if (minPrice.isNaN() || maxPrice.isNaN()) return false;
            if (minPrice > maxPrice) return false;
        }

        // Optional strings if provided should not be blank
        if (grouping != null && grouping.isBlank()) return false;
        if (presentationType != null && presentationType.isBlank()) return false;

        return true;
    }
}
