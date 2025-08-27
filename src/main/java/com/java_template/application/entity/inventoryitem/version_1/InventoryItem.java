package com.java_template.application.entity.inventoryitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class InventoryItem implements CyodaEntity {
    public static final String ENTITY_NAME = "InventoryItem"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Use String for textual and foreign-key/UUID-like values
    private String id; // technical id (serialized UUID)
    private String name;
    private String category;
    private String dateAdded; // ISO date string e.g., 2025-08-20
    private String location;
    private Double price;
    private Integer quantity;
    private String status; // use String for enum-like values (e.g., "VALIDATED")
    private String supplier;

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
        // Validate required string fields (use isBlank checks)
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Validate numeric fields
        if (quantity == null || quantity < 0) return false;
        if (price == null || price < 0.0) return false;
        return true;
    }
}