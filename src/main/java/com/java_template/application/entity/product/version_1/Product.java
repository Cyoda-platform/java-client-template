package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Boolean active;
    private Map<String, String> attributes;
    private String businessId; // business identifier (e.g., SKU)
    private String category;
    private String createdAt; // ISO-8601 timestamp as String
    private String currency;
    private String description;
    private List<String> images;
    private String name;
    private BigDecimal price;
    private Integer stockQuantity;
    private String updatedAt; // ISO-8601 timestamp as String

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
        if (businessId == null || businessId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (currency == null || currency.isBlank()) return false;

        // Validate numeric fields
        if (price == null) return false;
        try {
            if (price.signum() < 0) return false;
        } catch (ArithmeticException e) {
            return false;
        }

        if (stockQuantity == null) return false;
        if (stockQuantity < 0) return false;

        // attributes, images, createdAt, updatedAt and active are optional
        return true;
    }
}