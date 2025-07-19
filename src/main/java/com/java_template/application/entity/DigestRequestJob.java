package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.Map;
import java.util.UUID;

@Data
public class DigestRequestJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String email;
    private Map<String, String> metadata;
    private StatusEnum status;

    public DigestRequestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestRequestJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestRequestJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
