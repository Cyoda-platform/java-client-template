package com.java_template.application.entity.ticket.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Ticket implements CyodaEntity {
    public static final String ENTITY_NAME = "Ticket";
    public static final Integer ENTITY_VERSION = 1;

    private String bookingId;
    private String ticketNumber;
    private String status;

    public Ticket() {}

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
        if (ticketNumber == null || ticketNumber.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}}