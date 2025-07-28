package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.OffsetDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailReport implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String reportJobId; // foreign key reference
    private String recipient;
    private String subject;
    private String body;
    private OffsetDateTime sentTimestamp;
    private String status; // e.g., PENDING, SENT, FAILED

    public EmailReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (reportJobId == null || reportJobId.isBlank()) return false;
        if (recipient == null || recipient.isBlank()) return false;
        if (subject == null || subject.isBlank()) return false;
        if (body == null || body.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
