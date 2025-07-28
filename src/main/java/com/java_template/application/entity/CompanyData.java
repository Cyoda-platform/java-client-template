package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class CompanyData implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String businessId; // Finnish company business ID
    private String companyName; // Official company name
    private String companyType; // Type of the company
    private String registrationDate; // ISO 8601 date of registration
    private String status; // Active or Inactive
    private String lei; // Legal Entity Identifier, or "Not Available" if missing
    private String retrievalJobId; // Reference to the RetrievalJob technicalId that triggered this data creation

    public CompanyData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (businessId == null || businessId.isBlank()) return false;
        if (companyName == null || companyName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
