package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String id; // serialized UUID / technical id
    private String name;
    private String description;
    private String sku;
    private Integer availableQuantity;
    private Double price;
    private String warehouseId; // foreign key reference as serialized UUID

    public Product() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank checks
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (warehouseId == null || warehouseId.isBlank()) return false;

        // Validate numeric fields
        if (availableQuantity == null || availableQuantity < 0) return false;
        if (price == null || price < 0.0) return false;

        return true;
    }
}