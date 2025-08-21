package com.java_template.application.entity.eggtimer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

@Data
public class EggTimer implements CyodaEntity {
    public static final String ENTITY_NAME = "EggTimer";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String ownerUserId; // links to User.id (serialized UUID)
    private String boilType; // soft/medium/hard
    private String eggSize; // small/medium/large
    private Integer eggsCount; // number of eggs
    private Integer durationSeconds; // calculated or overridden
    private String startAt; // ISO timestamp when timer should start
    private String state; // created/scheduled/running/paused/completed/cancelled
    private String createdAt; // ISO timestamp

    // Added fields used by processors/criteria
    private String scheduledStartAt; // when scheduled to start (ISO)
    private String expectedEndAt; // when timer expected to end (ISO)
    private JsonNode metadata; // arbitrary metadata provided by client

    public EggTimer() {}

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
        if (this.ownerUserId == null || this.ownerUserId.isBlank()) return false;
        if (this.boilType == null || this.boilType.isBlank()) return false;
        if (this.eggSize == null || this.eggSize.isBlank()) return false;
        if (this.eggsCount == null || this.eggsCount < 1) return false;
        if (this.startAt == null || this.startAt.isBlank()) return false;
        if (this.state == null || this.state.isBlank()) return false;
        if (this.createdAt == null || this.createdAt.isBlank()) return false;
        return true;
    }
}
