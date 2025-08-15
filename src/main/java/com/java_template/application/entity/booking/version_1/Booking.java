package com.java_template.application.entity.booking.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Booking implements CyodaEntity {
    public static final String ENTITY_NAME = "Booking";
    public static final Integer ENTITY_VERSION = 1;

    private String userId;
    private String eventId;
    private Integer tickets;
    private String bookingDate;
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
        if (userId == null || userId.isBlank()) return false;
        if (eventId == null || eventId.isBlank()) return false;
        if (tickets == null || tickets <= 0) return false;
        if (bookingDate == null || bookingDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
