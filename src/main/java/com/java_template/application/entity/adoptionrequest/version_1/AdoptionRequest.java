package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String requestId; // business id
    private String petId; // foreign key to Pet.petId (serialized UUID)
    private String technicalId; // internal technical id used by workflow
    private String requesterName;
    private String requesterContact; // email/phone
    private String submittedAt; // datetime as String
    private String status; // submitted approved rejected cancelled
    private String notes;
    private Instant decisionAt; // time when a decision was taken
    private String reviewer; // assigned reviewer

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (petId == null || petId.isBlank()) return false;
        if (requesterName == null || requesterName.isBlank()) return false;
        if (requesterContact == null || requesterContact.isBlank()) return false;
        return true;
    }
}
