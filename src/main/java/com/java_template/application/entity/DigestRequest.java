package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestRequest implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String userId;
    private LocalDateTime requestTime;
    private StatusEnum status;
    private String externalApiUrl;
    private List<String> emailRecipients;
    private String emailTemplateId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DigestRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestRequest");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (status == null) return false;
        if (externalApiUrl == null || externalApiUrl.isBlank()) return false;
        if (emailRecipients == null || emailRecipients.isEmpty()) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
