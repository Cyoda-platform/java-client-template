package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailInteractionReport implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String subscriberId; // reference to subscriber by UUID string
    private String eventType; // delivery or open
    private OffsetDateTime eventTimestamp; // when the interaction occurred
    private String status; // report event state: RECORDED

    public EmailInteractionReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailInteractionReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailInteractionReport");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (subscriberId == null || subscriberId.isBlank()) {
            return false;
        }
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        if (eventTimestamp == null) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
