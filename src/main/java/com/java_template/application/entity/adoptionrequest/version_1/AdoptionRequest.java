package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;
    // Entity fields based on prototype
    private String requestId;          // technical id
    private String petId;              // foreign key (serialized UUID)
    private String userId;             // foreign key (serialized UUID)
    private Double adoptionFee;
    private Boolean homeVisitRequired;
    private String notes;
    private String paymentStatus;      // use String for enum-like values
    private String status;             // use String for enum-like values
    private String requestedAt;        // ISO timestamp as String

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
        // Validate required string fields (use isBlank for Strings)
        if (requestId == null || requestId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (requestedAt == null || requestedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;

        // Numeric and boolean validations
        if (adoptionFee == null || adoptionFee < 0) return false;
        if (homeVisitRequired == null) return false;

        return true;
    }
}