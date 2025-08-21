package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id (email or UUID)
    private String name; // display name
    private String timezone; // user timezone for scheduling

    // preferences
    private String defaultBoilType; // e.g., soft/medium/hard
    private String defaultEggSize; // e.g., small/medium/large
    private Boolean allowMultipleTimers;

    // Additional fields
    private String state; // ACTIVE/INACTIVE

    public User() {}

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
        if (this.name == null || this.name.isBlank()) return false;
        if (this.timezone == null || this.timezone.isBlank()) return false;
        if (this.defaultBoilType == null || this.defaultBoilType.isBlank()) return false;
        if (this.defaultEggSize == null || this.defaultEggSize.isBlank()) return false;
        if (this.allowMultipleTimers == null) return false;
        return true;
    }
}
