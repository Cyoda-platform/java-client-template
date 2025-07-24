package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PurrfectPetsJob implements CyodaEntity {
    private String technicalId; // datastore generated unique ID
    private String petStatus; // status filter for pet ingestion (available, pending, sold)
    private String requestedAt; // ISO timestamp when job was created
    private String status; // job status: PENDING, PROCESSING, COMPLETED, FAILED
    private String resultSummary; // summary or count of ingested pets

    public PurrfectPetsJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("purrfectPetsJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "purrfectPetsJob");
    }

    @Override
    public boolean isValid() {
        // Validate required String fields are not blank
        if (petStatus == null || petStatus.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        if (requestedAt == null || requestedAt.isBlank()) {
            return false;
        }
        return true;
    }
}
