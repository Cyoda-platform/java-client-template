package com.java_template.application.entity.subscriptor.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class Subscriptor implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriptor";
    public static final Integer ENTITY_VERSION = 1;

    // Business fields
    private String email; // primary identifier for human-friendly search
    private String name; // optional display name
    private Instant subscribedAt; // when the user subscribed
    private java.util.List<String> topics = new ArrayList<>(); // topics the subscriptor subscribed to
    private Boolean active = true; // whether the subscription is active

    // Standard metadata
    private Instant createdAt;
    private Instant updatedAt;

    public Subscriptor() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: email must be present and contain '@'
        if (this.email == null || this.email.isBlank()) return false;
        return this.email.contains("@");
    }
}
