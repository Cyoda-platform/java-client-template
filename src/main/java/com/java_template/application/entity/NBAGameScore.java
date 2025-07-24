package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class NBAGameScore implements CyodaEntity {

    private String gameDate; // Date of the NBA game, format YYYY-MM-DD
    private String homeTeam; // Name of the home team
    private String awayTeam; // Name of the away team
    private Integer homeScore; // Score of the home team
    private Integer awayScore; // Score of the away team
    private String status; // Game status, e.g., FINAL, IN_PROGRESS
    private String venue; // Location of the game

    public NBAGameScore() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("nbaGameScore");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "nbaGameScore");
    }

    @Override
    public boolean isValid() {
        if (gameDate == null || gameDate.isBlank()) return false;
        if (homeTeam == null || homeTeam.isBlank()) return false;
        if (awayTeam == null || awayTeam.isBlank()) return false;
        if (homeScore == null) return false;
        if (awayScore == null) return false;
        if (status == null || status.isBlank()) return false;
        if (venue == null || venue.isBlank()) return false;
        return true;
    }
}
