package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AdoptionRequest implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private Long petId; // reference to Pet
    private String requesterName;
    private String contactInfo;
    private StatusEnum status; // REQUESTED, APPROVED, REJECTED
    private LocalDateTime requestedAt;

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("adoptionRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "adoptionRequest");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
                && petId != null
                && requesterName != null && !requesterName.isBlank()
                && contactInfo != null && !contactInfo.isBlank()
                && status != null;
    }

    public enum StatusEnum {
        REQUESTED, APPROVED, REJECTED
    }
}
