package com.java_template.application.entity.bookingdates.version_1;

import lombok.Data;
import java.time.LocalDate;

/**
 * BookingDates represents the check-in and check-out dates for a booking.
 * This is an embedded object within the Booking entity.
 */
@Data
public class BookingDates {
    
    private LocalDate checkin;
    private LocalDate checkout;
    
    /**
     * Validates that the booking dates are valid.
     * @return true if dates are valid, false otherwise
     */
    public boolean isValid() {
        if (checkin == null || checkout == null) {
            return false;
        }
        return checkin.isBefore(checkout);
    }
}
