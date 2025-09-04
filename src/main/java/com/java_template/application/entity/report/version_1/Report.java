package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The Report entity represents generated reports containing booking analytics and summaries.
 * Reports can contain filtered booking data and calculated metrics.
 */
@Data
public class Report implements CyodaEntity {
    
    public static final String ENTITY_NAME = Report.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;
    
    // Report identification
    private String reportId;
    private String reportName;
    private String reportType; // SUMMARY, DETAILED, FILTERED
    
    // Report parameters
    private String dateRange;
    private String filterCriteria; // JSON string of applied filters
    
    // Calculated metrics
    private Integer totalBookings;
    private BigDecimal totalRevenue;
    private BigDecimal averagePrice;
    private Integer depositPaidCount;
    private Integer depositUnpaidCount;
    
    // System metadata
    private LocalDateTime generatedAt;
    private String generatedBy;
    
    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }
    
    @Override
    public boolean isValid() {
        // Basic validation - required fields must be present
        if (reportId == null || reportName == null || reportType == null) {
            return false;
        }
        
        // Report type validation
        if (!reportType.equals("SUMMARY") && !reportType.equals("DETAILED") && !reportType.equals("FILTERED")) {
            return false;
        }
        
        return true;
    }
}
