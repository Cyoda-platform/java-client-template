package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Workflow implements CyodaEntity {

    private String subscriberEmail; // Email of the user subscribing to daily notifications
    private String requestedDate; // Date for which NBA scores are requested, format YYYY-MM-DD
    private String status; // Workflow status: PENDING, PROCESSING, COMPLETED, FAILED
    private String createdAt; // Timestamp of workflow creation

    public Workflow() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("workflow");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "workflow");
    }

    @Override
    public boolean isValid() {
        if (subscriberEmail == null || subscriberEmail.isBlank()) return false;
        if (requestedDate == null || requestedDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}
