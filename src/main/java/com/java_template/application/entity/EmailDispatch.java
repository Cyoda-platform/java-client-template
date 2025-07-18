package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EmailDispatch implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String jobId; // foreign key reference to Job (UUID serialized as String)
    private String emailAddress;
    private String status; // e.g., QUEUED, SENT
    private LocalDateTime sentAt;

    public EmailDispatch() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailDispatch");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailDispatch");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() &&
               jobId != null && !jobId.isBlank() &&
               emailAddress != null && !emailAddress.isBlank() &&
               status != null && !status.isBlank();
    }
}
