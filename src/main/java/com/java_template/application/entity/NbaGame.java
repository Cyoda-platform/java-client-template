package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDate;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class NbaGame implements CyodaEntity {
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String status; // REPORTED, VERIFIED

    public NbaGame() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("nbaGame");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "nbaGame");
    }

    @Override
    public boolean isValid() {
        return gameDate != null && homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank() 
            && homeScore != null && awayScore != null && status != null && !status.isBlank();
    }
}
