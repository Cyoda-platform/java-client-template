package com.java_template.application.entity.user.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String userId; // application user id
    private String displayName; // user friendly name
    private String preferences; // JSON serialized preferences (preferred genres[], favoriteAuthors[])
    private Boolean optInReports; // weekly report opt-in
    private String lastActiveAt; // timestamp

    public User() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return userId != null && !userId.isBlank()
            && displayName != null && !displayName.isBlank();
    }
}
