package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;
import java.util.UUID;

@Data
public class Subscription implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String userEmail;
    private List<String> teams; // List of team codes or names

    public Subscription() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("subscription");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "subscription");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && userEmail != null && !userEmail.isBlank();
    }
}
