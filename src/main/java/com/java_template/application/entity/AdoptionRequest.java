package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class AdoptionRequest implements CyodaEntity {
    private String id;  // business ID
    private UUID technicalId;  // database ID

    private String requestId;
    private String petId;
    private String requesterName;
    private LocalDateTime requestDate;
    private String status;  // StatusEnum as String

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
        return id != null && !id.isBlank() &&
               requestId != null && !requestId.isBlank() &&
               petId != null && !petId.isBlank() &&
               requesterName != null && !requesterName.isBlank() &&
               requestDate != null &&
               status != null && !status.isBlank();
    }
}
