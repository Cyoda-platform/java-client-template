package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class GameScore implements CyodaEntity {
    private String date; // Date of the NBA game, format YYYY-MM-DD
    private String homeTeam; // Name of the home team
    private String awayTeam; // Name of the away team
    private Integer homeScore; // Score of the home team
    private Integer awayScore; // Score of the away team
    private String gameStatus; // Status of the game: Scheduled, Completed, Postponed

    public GameScore() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("gameScore");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "gameScore");
    }

    @Override
    public boolean isValid() {
        if (date == null || date.isBlank()) return false;
        if (homeTeam == null || homeTeam.isBlank()) return false;
        if (awayTeam == null || awayTeam.isBlank()) return false;
        if (homeScore == null) return false;
        if (awayScore == null) return false;
        if (gameStatus == null || gameStatus.isBlank()) return false;
        return true;
    }
}
