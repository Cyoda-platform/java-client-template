package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.Instant;

@Data
public class DigestContent implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String digestJobId; // foreign key to DigestJob
    private String requestId; // foreign key to DigestRequest
    private String content;
    private String format; // PLAIN_TEXT, HTML, ATTACHMENT
    private Instant createdAt;

    public DigestContent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestContent");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestContent");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (digestJobId == null || digestJobId.isBlank()) return false;
        if (requestId == null || requestId.isBlank()) return false;
        if (content == null || content.isBlank()) return false;
        if (format == null || format.isBlank()) return false;
        return true;
    }
}
