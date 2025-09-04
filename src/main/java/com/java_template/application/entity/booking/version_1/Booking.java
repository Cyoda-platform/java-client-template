package com.java_template.application.entity.booking.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The Booking entity represents a hotel booking retrieved from the Restful Booker API.
 * This entity stores all booking information and tracks the processing state through the workflow.
 */
@Data
public class Booking implements CyodaEntity {
    
    public static final String ENTITY_NAME = Booking.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;
    
    // Unique identifier from Restful Booker API
    private Integer bookingId;
    
    // Guest information
    private String firstname;
    private String lastname;
    
    // Booking details
    private Integer totalprice;
    private Boolean depositpaid;
    
    // Booking dates
    private LocalDate checkin;
    private LocalDate checkout;
    
    // Additional information
    private String additionalneeds;
    
    // System metadata
    private LocalDateTime retrievedAt;
    
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
        if (bookingId == null || firstname == null || lastname == null || 
            totalprice == null || depositpaid == null || 
            checkin == null || checkout == null) {
            return false;
        }
        
        // Business rule validation
        if (totalprice <= 0) {
            return false;
        }
        
        // Date validation
        if (!checkin.isBefore(checkout)) {
            return false;
        }
        
        return true;
    }
}
