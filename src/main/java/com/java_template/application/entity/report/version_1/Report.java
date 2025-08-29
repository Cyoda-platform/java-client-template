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
    private Double averageBookingPrice;
    private Integer bookingsCount;
    private List<BookingSummary> bookingsSample;
    private String createdBy;
    private Criteria criteria;
    private String generatedAt;
    private String jobTechnicalId; // serialized UUID / technical reference
    private String name;
    private String reportId; // serialized UUID / technical id
    private String status;
    private Double totalRevenue;
    private String visualizationUrl;

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
        if (name == null || name.isBlank()) return false;
        if (reportId == null || reportId.isBlank()) return false;
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;

        // Validate numeric fields when present
        if (averageBookingPrice != null && averageBookingPrice < 0) return false;
        if (bookingsCount != null && bookingsCount < 0) return false;
        if (totalRevenue != null && totalRevenue < 0) return false;

        // Validate criteria if present
        if (criteria != null) {
            if (criteria.getDateFrom() == null || criteria.getDateFrom().isBlank()) return false;
            if (criteria.getDateTo() == null || criteria.getDateTo().isBlank()) return false;
            // depositPaid may be null (meaning not filtered), so no check
        }

        // Validate bookings sample entries if present
        if (bookingsSample != null) {
            for (BookingSummary b : bookingsSample) {
                if (b == null) return false;
                if (b.getFirstname() == null || b.getFirstname().isBlank()) return false;
                if (b.getLastname() == null || b.getLastname().isBlank()) return false;
                if (b.getCheckin() == null || b.getCheckin().isBlank()) return false;
                if (b.getCheckout() == null || b.getCheckout().isBlank()) return false;
                if (b.getPersistedAt() == null || b.getPersistedAt().isBlank()) return false;
                // bookingId and totalprice can be null in some contexts, but if present must be non-negative
                if (b.getBookingId() != null && b.getBookingId() < 0) return false;
                if (b.getTotalprice() != null && b.getTotalprice() < 0) return false;
            }
        }

        return true;
    }

    @Data
    public static class Criteria {
        private String dateFrom;
        private String dateTo;
        private Boolean depositPaid;
        private Integer maxPrice;
        private Integer minPrice;
    }

    @Data
    public static class BookingSummary {
        private String additionalneeds;
        private Integer bookingId;
        private String checkin;
        private String checkout;
        private Boolean depositpaid;
        private String firstname;
        private String lastname;
        private String persistedAt;
        private String source;
        private Double totalprice;
    }
}