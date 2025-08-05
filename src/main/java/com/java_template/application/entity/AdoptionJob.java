package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class AdoptionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionJob";

    private String applicantName;
    private String applicantEmail;
    private String petId; // UUID as String
    private String applicationDate;
    private String status; // PENDING, APPROVED, REJECTED
    private String notes;

    public AdoptionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (applicantName == null || applicantName.isBlank()) return false;
        if (applicantEmail == null || applicantEmail.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (applicationDate == null || applicationDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
