package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDate;

@Data
public class GameScore implements CyodaEntity {
    private LocalDate gameDate; // date of the NBA game
    private String homeTeam; // name of the home team
    private String awayTeam; // name of the away team
    private Integer homeScore; // home team final score
    private Integer awayScore; // away team final score
    private String status; // RECEIVED, PROCESSED

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
        return gameDate != null && homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank()
            && homeScore != null && awayScore != null && status != null && !status.isBlank();
    }
}
