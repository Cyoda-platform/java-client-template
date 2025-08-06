package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class CatFactInteraction implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFactInteraction";

    private String subscriberEmail;     // email of subscriber interacting with the email
    private String catFactId;            // reference to the cat fact sent
    private String interactionType;     // e.g., OPEN, CLICK
    private String interactionTimestamp; // timestamp of the interaction

    public CatFactInteraction() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (subscriberEmail == null || subscriberEmail.isBlank()) return false;
        if (catFactId == null || catFactId.isBlank()) return false;
        if (interactionType == null || interactionType.isBlank()) return false;
        if (interactionTimestamp == null || interactionTimestamp.isBlank()) return false;
        return true;
    }
}
