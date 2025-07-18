package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class NbaGameScore implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String gameId; // unique NBA game identifier
    private LocalDate date; // game date
    private String homeTeam; // home team name
    private String awayTeam; // away team name
    private Integer homeScore; // home team score
    private Integer awayScore; // away team score
    private String status; // ScoreStatusEnum as String (RECEIVED, PROCESSED)

    public NbaGameScore() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("nbaGameScore");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "nbaGameScore");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && gameId != null && !gameId.isBlank() && date != null && homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank() && status != null && !status.isBlank();
    }
}
