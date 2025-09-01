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

    // Basic identifiers and metadata
    private String sku;
    private String name;
    private String category;
    private String warehouseId;

    // Descriptions and localization
    private String description;
    private Map<String, Localization> localizations; // key: locale code (e.g., "en", "fr")

    // Pricing and inventory
    private Double price;
    private Integer quantityAvailable;
    private Inventory inventory;

    // Attributes, options, relationships, bundles, media
    private Map<String, String> attributes;
    private Map<String, String> options;
    private Map<String, Boolean> compliance;
    private Map<String, Object> bundles; // kept generic; example shows empty array, using map for extensibility
    private Relationships relationships;
    private List<String> media;

    // Events and timestamps
    private List<Event> events;
    private String createdAt;
    private String updatedAt;

    // Additional fields from example
    private String myAdditionalField;
    private List<Variant> variants;

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
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;

        // Price and quantity validations
        if (price == null || price < 0) return false;
        if (quantityAvailable == null || quantityAvailable < 0) return false;

        // Inventory safety stock if provided must be non-negative
        if (inventory != null && inventory.getSafetyStock() != null && inventory.getSafetyStock() < 0)
            return false;

        // Validate variants if present
        if (variants != null) {
            for (Variant v : variants) {
                if (v == null) return false;
                if (v.getSku() == null || v.getSku().isBlank()) return false;
                if (v.getName() == null || v.getName().isBlank()) return false;
            }
        }

        // Validate events if present (simple check)
        if (events != null) {
            for (Event e : events) {
                if (e == null) return false;
                if (e.getType() == null || e.getType().isBlank()) return false;
                if (e.getAt() == null || e.getAt().isBlank()) return false;
            }
        }

        return true;
    }

    @Data
    public static class Event {
        private String at;
        private String type;
    }

    @Data
    public static class Inventory {
        private Integer safetyStock;
    }

    @Data
    public static class Localization {
        private String name;
        private String description;
    }

    @Data
    public static class Variant {
        private String name;
        private String sku;
    }

    @Data
    public static class Relationships {
        private List<String> relatedSkus;
    }
}