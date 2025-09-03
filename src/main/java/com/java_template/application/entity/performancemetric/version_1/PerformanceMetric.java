package com.java_template.application.entity.performancemetric.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PerformanceMetric implements CyodaEntity {
    public static final String ENTITY_NAME = PerformanceMetric.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Unique identifier
    private Long id;
    
    // Reference to Product entity
    private Long productId;
    
    // Type of metric (SALES_VOLUME, REVENUE, INVENTORY_TURNOVER, TREND_ANALYSIS)
    private String metricType;
    
    // Calculated metric value
    private BigDecimal metricValue;
    
    // Time period for calculation (DAILY, WEEKLY, MONTHLY)
    private String calculationPeriod;
    
    // Start date of calculation period
    private LocalDate periodStart;
    
    // End date of calculation period
    private LocalDate periodEnd;
    
    // When metric was calculated
    private LocalDateTime calculatedAt;
    
    // Whether metric represents an outlier/anomaly
    private Boolean isOutlier;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return productId != null && productId > 0 &&
               metricType != null && !metricType.trim().isEmpty() &&
               calculationPeriod != null && !calculationPeriod.trim().isEmpty() &&
               periodStart != null && periodEnd != null &&
               periodStart.isBefore(periodEnd) || periodStart.isEqual(periodEnd);
    }
}
