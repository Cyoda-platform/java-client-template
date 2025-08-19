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

    private String bookingId; // source booking id from Restful Booker
    private String customerName; // guest name
    private String checkInDate; // ISO date
    private String checkOutDate; // ISO date
    private Double totalPrice; // total booking price
    private Boolean depositPaid; // deposit indicator
    private String status; // CONFIRMED CANCELLED
    private String source; // e.g., RestfulBooker
    private String persistedAt; // ISO datetime

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
        if (bookingId == null || bookingId.isBlank()) return false;
        if (customerName == null || customerName.isBlank()) return false;
        if (checkInDate == null || checkInDate.isBlank()) return false;
        if (checkOutDate == null || checkOutDate.isBlank()) return false;
        if (totalPrice == null || totalPrice.isNaN()) return false;
        if (status == null || status.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        return true;
    }
}
