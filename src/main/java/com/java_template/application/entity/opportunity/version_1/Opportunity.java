package com.java_template.application.entity.opportunity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Opportunity implements CyodaEntity {
    public static final String ENTITY_NAME = "Opportunity";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String name;
    private String contactId; // serialized UUID reference to Contact
    private String leadId; // serialized UUID reference to Lead
    private Double amount;
    private String stage; // e.g., Prospecting, Proposal, Closed Won
    private String closeDate; // ISO date
    private String createdAt; // ISO timestamp

    public Opportunity() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        boolean hasReference = (contactId != null && !contactId.isBlank()) || (leadId != null && !leadId.isBlank());
        if (!hasReference) return false;
        if (amount == null || amount <= 0) return false;
        return true;
    }
}
