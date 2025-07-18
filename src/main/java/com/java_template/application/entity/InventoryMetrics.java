package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class InventoryMetrics implements CyodaEntity {
    private int totalItems;
    private double averagePrice;
    private double totalValue;

    public InventoryMetrics() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("inventoryMetrics");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "inventoryMetrics");
    }

    @Override
    public boolean isValid() {
        return totalItems >= 0
                && averagePrice >= 0
                && totalValue >= 0;
    }
}
