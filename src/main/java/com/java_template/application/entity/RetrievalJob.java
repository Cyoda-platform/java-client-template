package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class RetrievalJob implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String companyName; // The company name or partial name input for search
    private String requestTimestamp; // ISO 8601 timestamp when the job was created
    private String status; // Job status: PENDING, PROCESSING, COMPLETED, FAILED
    private String resultTechnicalId; // Reference to the enrichment result entity, nullable initially

    public RetrievalJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (companyName == null || companyName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
