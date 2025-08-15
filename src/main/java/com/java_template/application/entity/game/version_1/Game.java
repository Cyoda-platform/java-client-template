package com.java_template.application.entity.game.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Game implements CyodaEntity {
    public static final String ENTITY_NAME = "Game";
    public static final Integer ENTITY_VERSION = 1;
    // Game fields
    private String gameId; // external identifier from sportsdata.io or generated if absent
    private String date; // YYYY-MM-DD
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String status; // final, scheduled, in_progress
    private String venue;
    private String league; // e.g., NBA
    private String rawPayload; // full raw payload JSON as String
    private String lastUpdated; // ISO-8601 timestamp of last update persisted

    public Game() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // gameId and date are required
        return gameId != null && !gameId.isBlank()
            && date != null && !date.isBlank();
    }
}
