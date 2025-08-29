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
    private String firstname;
    private String lastname;
    private String checkin; // ISO date string (yyyy-MM-dd)
    private String checkout; // ISO date string (yyyy-MM-dd)
    private Boolean depositpaid;
    private Double totalprice;
    private String additionalneeds;
    private String persistedAt; // ISO timestamp string
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
        // Validate required string fields
        if (firstname == null || firstname.isBlank()) return false;
        if (lastname == null || lastname.isBlank()) return false;
        if (checkin == null || checkin.isBlank()) return false;
        if (checkout == null || checkout.isBlank()) return false;
        if (source == null || source.isBlank()) return false;

        // Validate numeric/boolean fields
        if (bookingId == null) return false;
        if (depositpaid == null) return false;
        if (totalprice == null || totalprice < 0) return false;

        // persistedAt and additionalneeds are optional
        return true;
    }
}