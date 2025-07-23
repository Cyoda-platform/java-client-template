package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PetAdoptionTask implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String petId; // reference to Pet
    private String taskType; // TaskTypeEnum (APPLICATION_RECEIVED, INTERVIEW_SCHEDULED, APPROVAL)
    private String status; // StatusEnum (PENDING, COMPLETED)
    private LocalDateTime createdAt;

    public PetAdoptionTask() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petAdoptionTask");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petAdoptionTask");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && petId != null && !petId.isBlank()
            && taskType != null && !taskType.isBlank()
            && status != null && !status.isBlank();
    }
}
