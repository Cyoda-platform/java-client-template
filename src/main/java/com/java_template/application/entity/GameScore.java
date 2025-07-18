package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class GameScore implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String gameId;
    private String date; // YYYY-MM-DD
    private String teamHome;
    private String teamAway;
    private Integer scoreHome;
    private Integer scoreAway;

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
        return id != null && !id.isBlank() 
            && gameId != null && !gameId.isBlank() 
            && date != null && !date.isBlank() 
            && teamHome != null && !teamHome.isBlank() 
            && teamAway != null && !teamAway.isBlank()
            && scoreHome != null && scoreAway != null;
    }
}
