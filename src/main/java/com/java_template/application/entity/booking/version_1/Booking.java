package com.java_template.application.entity.booking.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Booking implements CyodaEntity {
    public static final String ENTITY_NAME = "Booking"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Integer bookingId;
    private String firstName;
    private String lastName;
    private String additionalNeeds;
    private BookingDates bookingDates;
    private Boolean depositPaid;
    private Double totalPrice;
    private String persistedAt; // ISO-8601 timestamp as String
    private String source;

    public Booking() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // helper for string blank checks
        if (bookingId == null) return false;
        if (depositPaid == null) return false;
        if (totalPrice == null) return false;
        if (isBlank(firstName)) return false;
        if (isBlank(lastName)) return false;
        if (bookingDates == null) return false;
        if (!bookingDates.isValid()) return false;
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Data
    public static class BookingDates {
        private String checkin;  // ISO date as String (e.g., 2025-01-05)
        private String checkout; // ISO date as String (e.g., 2025-01-08)

        public BookingDates() {}

        public boolean isValid() {
            if (checkin == null || checkin.isBlank()) return false;
            if (checkout == null || checkout.isBlank()) return false;
            return true;
        }
    }
}