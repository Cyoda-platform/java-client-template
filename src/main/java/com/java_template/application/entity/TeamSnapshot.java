package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class TeamSnapshot implements CyodaEntity {
    public static final String ENTITY_NAME = "TeamSnapshot";

    private String season; // The Bundesliga season year this snapshot belongs to
    private String effectiveDate; // ISO date, the date this snapshot reflects
    private Integer teamId; // External football-data.org team ID
    private String teamName;
    private String venue; // Team venue
    private String crestUrl; // URL to team crest image
    private String createdAt; // Timestamp when snapshot was created

    public TeamSnapshot() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (season == null || season.isBlank()) return false;
        if (effectiveDate == null || effectiveDate.isBlank()) return false;
        if (teamName == null || teamName.isBlank()) return false;
        if (venue == null || venue.isBlank()) return false;
        if (crestUrl == null || crestUrl.isBlank()) return false;
        return true;
    }
}
