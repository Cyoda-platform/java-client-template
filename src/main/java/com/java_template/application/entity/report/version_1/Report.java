package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID)
    private String jobReference; // reference to ReportJob (serialized UUID)
    private String generatedAt; // ISO-8601 timestamp string
    private Metrics metrics;
    private List<Row> rows;
    private String storageLocation; // e.g., s3 path
    private String summary;
    private Visuals visuals;

    public Report() {} 

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
        if (id == null || id.isBlank()) return false;
        if (jobReference == null || jobReference.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (storageLocation == null || storageLocation.isBlank()) return false;

        // Validate metrics
        if (metrics == null) return false;
        if (metrics.getAveragePrice() == null) return false;
        if (metrics.getTotalItems() == null) return false;
        if (metrics.getTotalQuantity() == null) return false;
        if (metrics.getTotalValue() == null) return false;

        // Validate rows (allow empty list but not null)
        if (rows == null) return false;
        for (Row r : rows) {
            if (r == null) return false;
            if (r.getId() == null || r.getId().isBlank()) return false;
            if (r.getName() == null || r.getName().isBlank()) return false;
            if (r.getPrice() == null) return false;
            if (r.getQuantity() == null) return false;
            if (r.getValue() == null) return false;
        }

        // Validate visuals if provided
        if (visuals != null) {
            if (visuals.getChartType() == null || visuals.getChartType().isBlank()) return false;
            if (visuals.getReference() == null || visuals.getReference().isBlank()) return false;
        }

        return true;
    }

    @Data
    public static class Metrics {
        private Double averagePrice;
        private Integer totalItems;
        private Integer totalQuantity;
        private Double totalValue;
    }

    @Data
    public static class Row {
        private String id;
        private String name;
        private Double price;
        private Integer quantity;
        private Double value;
    }

    @Data
    public static class Visuals {
        private String chartType;
        private String reference;
    }
}