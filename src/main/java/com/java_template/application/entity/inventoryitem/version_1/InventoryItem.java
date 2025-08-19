package com.java_template.application.entity.inventoryitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class InventoryItem implements CyodaEntity {
    public static final String ENTITY_NAME = "InventoryItem";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String sku;
    private String name;
    private String category;
    private Integer quantity;
    private java.math.BigDecimal unitPrice;
    private String location;
    private OffsetDateTime lastUpdated;
    private String sourceId;

    public InventoryItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Check required business fields
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (quantity == null || quantity < 0) return false;
        if (lastUpdated == null) return false;
        if (sourceId == null || sourceId.isBlank()) return false;
        return true;
    }
}
