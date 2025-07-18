package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class GameScore implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String status; // Use ScoreStatusEnum in real code

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
        return id != null && !id.isBlank() && gameDate != null && homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank() && homeScore != null && awayScore != null && status != null && !status.isBlank();
    }
}
