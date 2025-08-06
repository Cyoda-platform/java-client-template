package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ChangeNotification implements CyodaEntity {
    public static final String ENTITY_NAME = "ChangeNotification";

    private String entityType; // e.g., "TeamSnapshot" or "SquadSnapshot"
    private String entityId; // ID of the changed snapshot entity
    private String changeType; // e.g., "ADDED", "REMOVED", "MODIFIED"
    private String detectedAt; // Timestamp of detection
    private String details; // Description or diff summary (optional)

    public ChangeNotification() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (entityType == null || entityType.isBlank()) return false;
        if (!entityType.equals("TeamSnapshot") && !entityType.equals("SquadSnapshot")) return false;
        if (entityId == null || entityId.isBlank()) return false;
        if (changeType == null || changeType.isBlank()) return false;
        if (detectedAt == null || detectedAt.isBlank()) return false;
        // details is optional, so no validation required
        return true;
    }
}
