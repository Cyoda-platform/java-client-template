package com.java_template.application.entity.team.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Team implements CyodaEntity {
    public static final String ENTITY_NAME = "Team";
    public static final Integer ENTITY_VERSION = 1;

    private String technicalId;
    private String team_id; // unique team identifier, optional mapping to external API id
    private String name; // team full name
    private String abbreviation; // e.g., LAL
    private String city;
    private String metadata; // JSON as String

    public Team() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        if (abbreviation == null || abbreviation.isBlank()) return false;
        return true;
    }
}
