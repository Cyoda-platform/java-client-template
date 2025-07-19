package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class EmailDispatch implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String jobId; // reference to DigestRequestJob as serialized UUID string
    private EmailFormatEnum emailFormat;
    private StatusEnum status;

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
        if (id == null || id.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (emailFormat == null) return false;
        if (status == null) return false;
        return true;
    }

    public enum EmailFormatEnum {
        PLAIN_TEXT, HTML, ATTACHMENT
    }

    public enum StatusEnum {
        QUEUED, SENT, FAILED
    }
}
