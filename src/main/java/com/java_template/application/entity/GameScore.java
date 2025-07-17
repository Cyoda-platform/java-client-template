package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class GameScore implements CyodaEntity {
    private String date;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;

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
        // Basic validation: date should not be null or blank, teams should not be null or blank, scores should not be negative
        if (date == null || date.isBlank()) return false;
        if (homeTeam == null || homeTeam.isBlank()) return false;
        if (awayTeam == null || awayTeam.isBlank()) return false;
        if (homeScore != null && homeScore < 0) return false;
        if (awayScore != null && awayScore < 0) return false;
        return true;
    }
}}