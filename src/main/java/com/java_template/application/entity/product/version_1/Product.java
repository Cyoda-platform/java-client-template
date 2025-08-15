package com.java_template.application.entity.product.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String sku;
    private String name;
    private String description;
    private Double price;
    private String currency;
    private Integer stockQuantity;
    private Double weight;
    private String dimensions;
    private String category;
    private List<String> images = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();
    private String importSource;

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
        if (id == null || id.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (stockQuantity == null || stockQuantity < 0) return false;
        return true;
    }
}
