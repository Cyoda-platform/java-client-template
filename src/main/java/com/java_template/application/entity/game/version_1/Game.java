package com.java_template.application.entity.game.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Game implements CyodaEntity {
    public static final String ENTITY_NAME = "Game";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String game_id; // source game identifier from external API
    private String date; // YYYY-MM-DD
    private String home_team;
    private String away_team;
    private Integer home_score;
    private Integer away_score;
    private String status;
    private String venue;
    private String raw_payload; // JSON as String
    private String fetched_at; // ISO timestamp
    private String persisted_at; // ISO timestamp

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
        if (game_id == null || game_id.isBlank()) return false;
        if (date == null || date.isBlank()) return false;
        if (home_team == null || home_team.isBlank()) return false;
        if (away_team == null || away_team.isBlank()) return false;
        return true;
    }
}
