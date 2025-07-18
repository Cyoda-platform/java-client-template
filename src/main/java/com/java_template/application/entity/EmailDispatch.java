package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailDispatch implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String jobId; // foreign key to DigestJob
    private String recipient;
    private String subject;
    private String body;
    private String status; // use String for status enum representation

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
        return id != null && !id.isBlank() && technicalId != null
                && jobId != null && !jobId.isBlank()
                && recipient != null && !recipient.isBlank()
                && subject != null && !subject.isBlank()
                && body != null && !body.isBlank()
                && status != null && !status.isBlank();
    }
}
