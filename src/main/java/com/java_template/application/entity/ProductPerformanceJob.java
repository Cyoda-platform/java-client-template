package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class ProductPerformanceJob implements CyodaEntity {
    private LocalDateTime requestDate;
    private String status;
    private String reportFormat;
    private String emailRecipient;
    private String scheduledDay;

    public ProductPerformanceJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("productPerformanceJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "productPerformanceJob");
    }

    @Override
    public boolean isValid() {
        if (requestDate == null) return false;
        if (status == null || status.isBlank()) return false;
        if (reportFormat == null || reportFormat.isBlank()) return false;
        if (emailRecipient == null || emailRecipient.isBlank()) return false;
        if (scheduledDay == null || scheduledDay.isBlank()) return false;
        return true;
    }
}
