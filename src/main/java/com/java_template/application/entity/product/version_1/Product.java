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
    // Technical identifier for the product (serialized UUID or business id)
    private String productId;
    // Human-readable product name
    private String name;
    // Product category (e.g., Food)
    private String category;
    // Arbitrary JSON metadata stored as String
    private String metadata;
    // Price in currency units (nullable if unknown)
    private Double price;

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
        // productId and name must be present and non-blank; price must be present and non-negative
        if (productId == null || productId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null) return false;
        if (price < 0.0) return false;
        return true;
    }
}