package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class CompanySearchJob implements CyodaEntity {
    // Entity fields
    private String companyName;
    private String status;
    private String createdAt;
    private String completedAt;
    private String outputFormat;

    public CompanySearchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("companySearchJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "companySearchJob");
    }

    @Override
    public boolean isValid() {
        if (companyName == null || companyName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (outputFormat == null || outputFormat.isBlank()) return false;
        return true;
    }
}
