package com.java_template.application.entity.bookingjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class BookingJob implements CyodaEntity {
    public static final String ENTITY_NAME = "BookingJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String jobName;
    private Map<String, Object> sourceBookingRequest;
    private String technicalId;
    private String createdBy;
    private String createdAt;
    private String status;
    private Integer progress;
    private Map<String, Object> result;
    private String startedAt;
    private String finishedAt;

    public BookingJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // technicalId and createdBy are required strings
        if (technicalId == null || technicalId.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;
        // sourceBookingRequest must contain minimum required fields: eventId, userId, tickets
        if (sourceBookingRequest == null) return false;
        Object eventIdObj = sourceBookingRequest.get("eventId");
        if (!(eventIdObj instanceof String) || ((String) eventIdObj).isBlank()) return false;
        Object userIdObj = sourceBookingRequest.get("userId");
        if (!(userIdObj instanceof String) || ((String) userIdObj).isBlank()) return false;
        Object ticketsObj = sourceBookingRequest.get("tickets");
        if (ticketsObj == null) return false;
        try {
            int tickets;
            if (ticketsObj instanceof Number) tickets = ((Number) ticketsObj).intValue();
            else tickets = Integer.parseInt(ticketsObj.toString());
            if (tickets <= 0) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
