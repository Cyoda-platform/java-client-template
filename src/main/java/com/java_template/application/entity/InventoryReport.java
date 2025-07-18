package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;
import java.util.List;

@Data
public class InventoryReport implements CyodaEntity {
    private String reportId;
    private Instant generatedAt;
    private String status;
    private InventoryMetrics metrics;
    private List<InventoryItem> data;

    public InventoryReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("inventoryReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "inventoryReport");
    }

    @Override
    public boolean isValid() {
        return reportId != null && !reportId.isEmpty()
                && generatedAt != null
                && status != null && !status.isEmpty();
    }
}
