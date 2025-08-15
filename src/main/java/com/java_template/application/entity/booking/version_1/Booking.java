package com.java_template.application.entity.booking.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Booking implements CyodaEntity {
    public static final String ENTITY_NAME = "Booking";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String bookingId;
    private String eventId;
    private String userId;
    private Integer tickets;
    private Double totalAmount;
    private String paymentStatus;
    private String bookingDate;
    private List<String> ticketCodes;
    private String createdAt;
    private String updatedAt;
    private String status;

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
        if (eventId == null || eventId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (tickets == null || tickets <= 0) return false;
        if (bookingDate == null || bookingDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
