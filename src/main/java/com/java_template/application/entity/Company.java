package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Company implements CyodaEntity {
    // Entity fields
    private String companyName;
    private String businessId;
    private String companyType;
    private String registrationDate;
    private String status;
    private String lei;

    public Company() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("company");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "company");
    }

    @Override
    public boolean isValid() {
        if (companyName == null || companyName.isBlank()) return false;
        if (businessId == null || businessId.isBlank()) return false;
        if (companyType == null || companyType.isBlank()) return false;
        if (registrationDate == null || registrationDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // lei can be "Not Available" so blank check not strict here
        if (lei == null) return false;
        return true;
    }
}
