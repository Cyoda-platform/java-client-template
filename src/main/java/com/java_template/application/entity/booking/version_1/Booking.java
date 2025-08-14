package com.java_template.application.entity.booking.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Booking implements CyodaEntity {
    public static final String ENTITY_NAME = "Booking";
    public static final Integer ENTITY_VERSION = 1;

    private String eventId;
    private String userId;
    private String bookingDate;
    private Integer numberOfTickets;
    private String bookingStatus;

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
        return !(eventId == null || eventId.isBlank()) 
            && !(userId == null || userId.isBlank()) 
            && !(bookingDate == null || bookingDate.isBlank()) 
            && numberOfTickets != null && numberOfTickets > 0 
            && !(bookingStatus == null || bookingStatus.isBlank());
    }
}
