package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class SquadSnapshot implements CyodaEntity {
    public static final String ENTITY_NAME = "SquadSnapshot";

    private String teamSnapshotId; // Reference to TeamSnapshot entity
    private Integer playerId; // External football-data.org player ID
    private String playerName;
    private String position;
    private String dateOfBirth; // ISO date
    private String nationality;
    private Integer squadNumber; // If available
    private String contractStartDate; // ISO date, if available
    private String contractEndDate; // ISO date, if available
    private String createdAt; // Timestamp when snapshot was created

    public SquadSnapshot() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (teamSnapshotId == null || teamSnapshotId.isBlank()) return false;
        if (playerName == null || playerName.isBlank()) return false;
        if (position == null || position.isBlank()) return false;
        if (dateOfBirth == null || dateOfBirth.isBlank()) return false;
        if (nationality == null || nationality.isBlank()) return false;
        return true;
    }
}
