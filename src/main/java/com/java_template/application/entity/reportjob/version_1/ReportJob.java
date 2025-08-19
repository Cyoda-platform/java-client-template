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
        if (jobName == null || jobName.isBlank()) return false;
        if (initiatedBy == null || initiatedBy.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        if (minPrice != null && maxPrice != null) {
            if (minPrice.isNaN() || maxPrice.isNaN()) return false;
            if (minPrice > maxPrice) return false;
        }

        if (grouping != null && grouping.isBlank()) return false;
        if (presentationType != null && presentationType.isBlank()) return false;

        return true;
    }
}
