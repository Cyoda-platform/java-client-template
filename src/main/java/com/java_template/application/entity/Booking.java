package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Booking implements CyodaEntity {
    private int bookingId;
    private String firstName;
    private String lastName;
    private int totalPrice;
    private boolean depositPaid;
    private String checkin;
    private String checkout;

    public Booking() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("booking");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "booking");
    }

    @Override
    public boolean isValid() {
        // Validate bookingId must be positive
        if (bookingId <= 0) return false;
        // Validate firstName and lastName are not null or empty
        if (firstName == null || firstName.isEmpty()) return false;
        if (lastName == null || lastName.isEmpty()) return false;
        // Validate totalPrice non-negative
        if (totalPrice < 0) return false;
        // Validate checkin and checkout dates are non-null and checkin is before or equal to checkout
        if (checkin == null || checkin.isEmpty()) return false;
        if (checkout == null || checkout.isEmpty()) return false;
        try {
            java.time.LocalDate checkinDate = java.time.LocalDate.parse(checkin);
            java.time.LocalDate checkoutDate = java.time.LocalDate.parse(checkout);
            if (checkinDate.isAfter(checkoutDate)) return false;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
        return true;
    }
}