package com.java_template.application.entity.report_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Report Entity - Represents generated performance analysis reports with aggregated metrics and insights
 */
@Data
public class ReportEntity implements CyodaEntity {
    public static final String ENTITY_NAME = ReportEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core report data
    private String reportId;
    private String reportType;
    private LocalDateTime generationDate;
    private ReportPeriod reportPeriod;
    private PerformanceMetrics metrics;
    private List<Insight> insights;
    private String filePath;
    private String fileFormat;
    private Long fileSize;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        if (reportId == null || reportId.trim().isEmpty()) {
            return false;
        }
        if (reportType == null || reportType.trim().isEmpty()) {
            return false;
        }
        if (generationDate == null) {
            return false;
        }
        if (reportPeriod == null) {
            return false;
        }
        if (reportPeriod.getStartDate() == null || reportPeriod.getEndDate() == null) {
            return false;
        }
        // Report period end date must be after start date
        if (reportPeriod.getEndDate().isBefore(reportPeriod.getStartDate()) || 
            reportPeriod.getEndDate().equals(reportPeriod.getStartDate())) {
            return false;
        }
        if (fileFormat == null || fileFormat.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Nested class for report time period
     */
    @Data
    public static class ReportPeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    /**
     * Nested class for aggregated performance metrics
     */
    @Data
    public static class PerformanceMetrics {
        private Double totalSales;
        private Double totalRevenue;
        private List<String> topSellingPets;
        private List<String> slowMovingPets;
        private Double inventoryTurnover;
    }

    /**
     * Nested class for business insights
     */
    @Data
    public static class Insight {
        private String category;
        private String description;
        private String priority;
        private Boolean actionRequired;
    }
}
