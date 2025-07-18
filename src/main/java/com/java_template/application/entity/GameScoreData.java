package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class GameScoreData implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String date; // YYYY-MM-DD
    private List<Game> games;

    public GameScoreData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("gameScoreData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "gameScoreData");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank() || date == null || date.isBlank() || games == null || games.isEmpty()) {
            return false;
        }
        for (Game game : games) {
            if (!game.isValid()) {
                return false;
            }
        }
        return true;
    }

    @Data
    public static class Game {
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;

        public boolean isValid() {
            return homeTeam != null && !homeTeam.isBlank() &&
                   awayTeam != null && !awayTeam.isBlank() &&
                   homeScore != null && awayScore != null;
        }
    }
}
