package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String reportId;
    private String jobReference; // foreign key (serialized UUID or external id)
    private String generatedAt; // ISO timestamp
    private String exportedAt; // ISO timestamp
    private Metrics metrics;
    private List<BookingRow> rows;
    private FilterCriteria scope;
    private Visualizations visualizations;

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
        // reportId and generatedAt are required
        if (reportId == null || reportId.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;

        // If jobReference or exportedAt are present they must not be blank
        if (jobReference != null && jobReference.isBlank()) return false;
        if (exportedAt != null && exportedAt.isBlank()) return false;

        // rows must be present (report with no rows is considered invalid)
        if (rows == null) return false;
        for (BookingRow row : rows) {
            if (row == null) return false;
            if (row.getBookingId() == null) return false;
            if (row.getBookingDates() == null) return false;
            BookingDates bd = row.getBookingDates();
            if (bd.getCheckin() == null || bd.getCheckin().isBlank()) return false;
            if (bd.getCheckout() == null || bd.getCheckout().isBlank()) return false;
            // names can be blank/empty in some datasets, so don't enforce non-blank here
        }

        // metrics if present should have sensible numeric values (basic checks)
        if (metrics != null) {
            if (metrics.getBookingCount() != null && metrics.getBookingCount() < 0) return false;
            if (metrics.getAveragePrice() != null && metrics.getAveragePrice() < 0) return false;
            if (metrics.getTotalRevenue() != null && metrics.getTotalRevenue() < 0) return false;
            // bookingsByRange may be null or empty; no strict validation
        }

        // scope if present: dateRange fields must not be blank when provided
        if (scope != null && scope.getDateRange() != null) {
            DateRange dr = scope.getDateRange();
            if (dr.getFrom() == null || dr.getFrom().isBlank()) return false;
            if (dr.getTo() == null || dr.getTo().isBlank()) return false;
        }

        // visualizations if present: validate chartData.type if provided
        if (visualizations != null && visualizations.getChartData() != null) {
            ChartData cd = visualizations.getChartData();
            if (cd.getType() != null && cd.getType().isBlank()) return false;
            // data.points may be null/empty; no strict validation
        }

        return true;
    }

    @Data
    public static class Metrics {
        private Double averagePrice;
        private Integer bookingCount;
        private Map<String, Integer> bookingsByRange;
        private Double totalRevenue;
    }

    @Data
    public static class BookingRow {
        private Integer bookingId;
        private BookingDates bookingDates;
        private Boolean depositPaid;
        private String firstName;
        private String lastName;
        private Double totalPrice;
    }

    @Data
    public static class BookingDates {
        private String checkin;
        private String checkout;
    }

    @Data
    public static class FilterCriteria {
        private String customerName;
        private DateRange dateRange;
        private String depositStatus; // enum-like, use String
        private Integer maxPrice;
        private Integer minPrice;
    }

    @Data
    public static class DateRange {
        private String from;
        private String to;
    }

    @Data
    public static class Visualizations {
        private ChartData chartData;
        private List<BookingRow> tableData;
    }

    @Data
    public static class ChartData {
        private ChartDataContainer data;
        private String type;

        @Data
        public static class ChartDataContainer {
            // points represented as list of lists, e.g. [ [ "2025-01-01", 5 ], [ "2025-01-07", 10 ] ]
            private List<List<Object>> points;
            // additional arbitrary fields may be present
            private Map<String, Object> data;
        }
    }
}