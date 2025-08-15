package com.java_template.application.entity.notification.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Notification implements CyodaEntity {
    public static final String ENTITY_NAME = "Notification";
    public static final Integer ENTITY_VERSION = 1;
    // Notification fields
    private String date; // YYYY-MM-DD the notification summarizes
    private String summaryText; // text content of the email summary
    private Integer recipientsCount; // number of subscribers targeted
    private String sentAt; // ISO-8601 timestamp when notification was sent
    private String status; // pending, sending, sent, failed
    private String payload; // email payload or structured summary as JSON string
    private Integer attemptCount; // number of send attempts

    public Notification() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // date and summaryText should be present for a Notification
        return date != null && !date.isBlank()
            && summaryText != null && !summaryText.isBlank();
    }
}
