package com.java_template.application.entity.notification.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Notification implements CyodaEntity {
    public static final String ENTITY_NAME = "Notification";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String timerId; // links to EggTimer.id (serialized UUID)
    private String userId; // links to User.id (serialized UUID)
    private String notifyAt; // ISO timestamp
    private String method; // alarm/sound/visual
    private Boolean delivered;
    private Integer snoozeCount;

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
        if (this.id == null || this.id.isBlank()) return false;
        if (this.timerId == null || this.timerId.isBlank()) return false;
        if (this.userId == null || this.userId.isBlank()) return false;
        if (this.notifyAt == null || this.notifyAt.isBlank()) return false;
        if (this.method == null || this.method.isBlank()) return false;
        if (this.delivered == null) return false;
        if (this.snoozeCount == null || this.snoozeCount < 0) return false;
        return true;
    }
}
