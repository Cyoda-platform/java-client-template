package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDate;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class GameScore implements CyodaEntity {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String status; // use String instead of enum

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
        if (gameDate == null) {
            return false;
        }
        if (homeTeam == null || homeTeam.isBlank()) {
            return false;
        }
        if (awayTeam == null || awayTeam.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
