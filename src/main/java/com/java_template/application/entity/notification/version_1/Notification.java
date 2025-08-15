package com.java_template.application.entity.notification.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Notification implements CyodaEntity {
    public static final String ENTITY_NAME = "Notification";
    public static final Integer ENTITY_VERSION = 1;

    private String technicalId;
    private String notification_id;
    private String date; // YYYY-MM-DD
    private String summary;
    private Integer recipients_count;
    private String sent_at; // ISO timestamp
    private String status; // PENDING, PREPARING, SENT, FAILED
    private String payload; // JSON as String

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
        if (date == null || date.isBlank()) return false;
        if (summary == null || summary.isBlank()) return false;
        return true;
    }
}
