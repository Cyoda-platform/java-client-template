package com.java_template.application.entity.inventory.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Inventory implements CyodaEntity {
    public static final String ENTITY_NAME = "Inventory";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String productId; // serialized UUID
    private Integer stockQuantity;
    private Integer reservedQuantity;
    private String warehouseLocation;

    public Inventory() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (productId == null || productId.isBlank()) return false;
        if (stockQuantity == null || stockQuantity < 0) return false;
        if (reservedQuantity == null || reservedQuantity < 0) return false;
        return true;
    }
}
