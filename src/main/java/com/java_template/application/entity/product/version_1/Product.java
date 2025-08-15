package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // UUID
    private String sku; // stock keeping unit
    private String name; // product title
    private String description; // detailed description
    private BigDecimal price; // unit price
    private String currency; // ISO currency code
    private Integer availableQuantity; // stock available
    private Boolean active; // catalog active flag
    private String createdAt; // ISO8601

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
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null || price.compareTo(java.math.BigDecimal.ZERO) <= 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (availableQuantity == null || availableQuantity < 0) return false;
        return true;
    }
}
