package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Match implements CyodaEntity {

    private String homeTeamId;
    private String awayTeamId;
    private String date;
    private Integer homeScore;
    private Integer awayScore;

    public Match() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("match");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "match");
    }

    @Override
    public boolean isValid() {
        return homeTeamId != null && !homeTeamId.isBlank() && awayTeamId != null && !awayTeamId.isBlank() && date != null && !date.isBlank();
    }
}
