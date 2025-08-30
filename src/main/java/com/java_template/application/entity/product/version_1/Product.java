package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Top-level fields
    private String name;
    private String sku;
    private Double price;
    private Integer quantityAvailable;
    private String category;
    private String description;
    private String warehouseId;

    // Collections & nested structures
    private List<String> media;
    private List<Map<String, Object>> bundles;
    private List<Map<String, Object>> variants;
    private List<Object> events;

    // Flexible maps for structured sub-objects
    private Map<String, Object> attributes; // e.g., brand, custom, dimensions, hazards, model, weight
    private Map<String, Object> compliance;
    private Map<String, Object> inventory;
    private Map<String, Object> options;
    private Map<String, Object> relationships;

    // Localizations mapped by locale code (e.g., "en", "fr")
    private Map<String, Localization> localizations;

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
        // Required string fields: name and sku must be present and not blank
        if (name == null || name.isBlank()) {
            return false;
        }
        if (sku == null || sku.isBlank()) {
            return false;
        }

        // Price must be provided and non-negative
        if (price == null || price < 0.0) {
            return false;
        }

        // Quantity must be provided and non-negative
        if (quantityAvailable == null || quantityAvailable < 0) {
            return false;
        }

        // warehouseId is optional in many contexts; if provided, ensure it's not blank
        if (warehouseId != null && warehouseId.isBlank()) {
            return false;
        }

        return true;
    }

    @Data
    public static class Localization {
        private String name;
        private String description;
    }
}