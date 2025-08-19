package com.java_template.application.entity.inventoryitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class InventoryItem implements CyodaEntity {
    public static final String ENTITY_NAME = "InventoryItem";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String technicalId;
    private String sku; // unique product code from upstream API
    private String name; // product display name
    private String category; // categorization for grouping
    private Integer quantity; // available units
    private BigDecimal unitPrice; // price per unit, may be null
    private String location; // warehouse/location
    private OffsetDateTime lastUpdated; // timestamp from source
    private String sourceId; // origin identifier from SwaggerHub API

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
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (quantity == null || quantity < 0) return false;
        if (sourceId == null || sourceId.isBlank()) return false;
        return true;
    }
}
