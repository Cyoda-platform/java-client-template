package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class PetJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String type; // type of job, e.g., "AddPet", "UpdatePetStatus"
    private String payload; // JSON string with job details
    private StatusEnum status; // PENDING, PROCESSING, COMPLETED, FAILED

    public PetJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
