package com.java_template.application.entity.product_performance_report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ProductPerformanceReport Entity
 * Represents a weekly product performance analysis and reporting entity
 * for the Pet Store API data analysis system.
 */
@Data
public class ProductPerformanceReport implements CyodaEntity {
    public static final String ENTITY_NAME = "ProductPerformanceReport";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique report ID
    private String reportId;

    // Report metadata
    private String reportWeek;  // e.g., "2025-W05"
    private LocalDateTime reportGeneratedAt;
    private String recipientEmail;

    // Extracted data
    private List<ProductMetric> productMetrics;
    private List<CategoryAnalysis> categoryAnalyses;

    // Analysis results
    private Double totalSalesVolume;
    private Double totalRevenue;
    private Double averageInventoryTurnover;
    private List<String> topSellingProducts;
    private List<RestockingRecommendation> restockingRecommendations;

    // Report content
    private String reportContent;  // PDF content or path
    private String reportSummary;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return reportId != null && !reportId.isBlank();
    }

    @Data
    public static class ProductMetric {
        private String productId;
        private String productName;
        private String category;
        private Integer salesVolume;
        private Double revenue;
        private Integer currentStock;
        private Double inventoryTurnover;
        private String performanceStatus;  // "high", "medium", "low"
    }

    @Data
    public static class CategoryAnalysis {
        private String categoryName;
        private Integer totalProducts;
        private Double categoryRevenue;
        private Double averageTurnover;
        private List<String> topProducts;
    }

    @Data
    public static class RestockingRecommendation {
        private String productId;
        private String productName;
        private Integer recommendedQuantity;
        private String urgency;  // "high", "medium", "low"
        private String reason;
    }
}

