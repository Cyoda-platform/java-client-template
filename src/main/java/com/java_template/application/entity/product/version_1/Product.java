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
    private Boolean active;
    private Integer availableQuantity;
    private String createdAt; // ISO-8601 timestamp as string
    private String description;
    private String name;
    private Double price;
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
        // Validate required string fields using isBlank()
        if (name == null || name.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate numeric and boolean fields
        if (price == null || price < 0) return false;
        if (availableQuantity == null || availableQuantity < 0) return false;
        if (active == null) return false;

        // description can be optional (allow blank/null)
        return true;
    }
}