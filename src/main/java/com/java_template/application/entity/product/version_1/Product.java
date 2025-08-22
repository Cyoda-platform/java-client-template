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
    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer availableQuantity;
    private String currency;

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
        // id and name must be present
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        // currency should be present
        if (currency == null || currency.isBlank()) return false;
        // price must be provided and non-negative
        if (price == null || price < 0) return false;
        // availableQuantity must be provided and non-negative
        if (availableQuantity == null || availableQuantity < 0) return false;
        return true;
    }
}