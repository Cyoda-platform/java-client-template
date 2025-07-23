package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Interaction implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String subscriberId; // UUID string foreign key
    private String catFactJobId;  // UUID string foreign key
    private String interactionType;
    private LocalDateTime interactedAt;
    private String status; // Could be enum, use String for simplicity

    public Interaction() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("interaction");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "interaction");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && subscriberId != null && !subscriberId.isBlank()
            && catFactJobId != null && !catFactJobId.isBlank()
            && interactionType != null && !interactionType.isBlank()
            && interactedAt != null
            && status != null && !status.isBlank();
    }
}
