package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PetAdoptionJob implements CyodaEntity {
    private String id; // business ID - jobId
    private UUID technicalId; // database ID

    private String petId;
    private String adopterName;
    private String adopterContact;
    private JobStatusEnum status;

    public PetAdoptionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petAdoptionJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petAdoptionJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && petId != null && !petId.isBlank()
            && adopterName != null && !adopterName.isBlank()
            && adopterContact != null && !adopterContact.isBlank()
            && status != null;
    }
}
