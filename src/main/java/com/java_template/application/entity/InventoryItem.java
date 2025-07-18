package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class InventoryItem implements CyodaEntity {
    private String itemId;
    private String name;
    private String category;
    private double price;
    private int quantity;

    public InventoryItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("inventoryItem");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "inventoryItem");
    }

    @Override
    public boolean isValid() {
        return itemId != null && !itemId.isEmpty()
                && name != null && !name.isEmpty()
                && category != null && !category.isEmpty()
                && price >= 0
                && quantity >= 0;
    }
}
