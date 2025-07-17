package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class AnalyzeRequest implements CyodaEntity {
    private String triggerDate;

    public AnalyzeRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("analyzeRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "analyzeRequest");
    }

    @Override
    public boolean isValid() {
        // Basic validation: triggerDate should be in YYYY-MM-DD format or null
        if (triggerDate == null || triggerDate.isEmpty()) return true;
        return triggerDate.matches("\\d{4}-\\d{2}-\\d{2}");
    }
}