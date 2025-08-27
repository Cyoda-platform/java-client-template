package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // technical id (returned by POST operations)
    private String productId;
    private String category;
    private String description;
    private String name;
    private Double price;
    private Integer quantityAvailable;
    private String sku;

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
        // Validate required string fields
        if (name == null || name.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        // description may be optional

        // Validate numeric fields
        if (price == null || price < 0.0) return false;
        if (quantityAvailable == null || quantityAvailable < 0) return false;

        return true;
    }
}