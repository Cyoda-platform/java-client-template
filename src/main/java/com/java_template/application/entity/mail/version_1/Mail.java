package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.time.OffsetDateTime;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Boolean isHappy; // nullable; set by evaluation criteria
    private List<String> mailList; // recipient addresses
    private String status; // current workflow state
    private Integer attemptCount; // number of send attempts
    private OffsetDateTime lastAttemptAt; // timestamp of last send attempt

    public Mail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // mailList must be present and contain non-blank addresses
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        for (String addr : mailList) {
            if (addr == null || addr.isBlank()) {
                return false;
            }
        }
        // attemptCount, if present, must be non-negative
        if (attemptCount != null && attemptCount < 0) {
            return false;
        }
        // status, if present, must not be blank
        if (status != null && status.isBlank()) {
            return false;
        }
        return true;
    }
}
