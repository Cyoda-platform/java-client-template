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

    // Technical id returned by POST endpoints (serialized UUID)
    private String id;

    // Business identifiers
    private String sku;
    private String name;
    private String category;
    private String description;
    private String warehouseId; // serialized UUID or identifier

    // Pricing & inventory
    private Double price;
    private Integer quantityAvailable;

    // Media and relationships
    private List<String> media;
    private Relationships relationships;

    // Rich attributes
    private Attributes attributes;
    private List<Bundle> bundles;
    private Compliance compliance;
    private List<Event> events;
    private Inventory inventory;
    private Localizations localizations;
    private Options options;
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
        // Required basic checks
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (quantityAvailable == null || quantityAvailable < 0) return false;

        // Optional String fields if provided must not be blank
        if (category != null && category.isBlank()) return false;
        if (warehouseId != null && warehouseId.isBlank()) return false;
        if (description != null && description.isBlank()) return false;

        // Validate variants
        if (variants != null) {
            for (Variant v : variants) {
                if (v == null) return false;
                if (v.variantSku == null || v.variantSku.isBlank()) return false;
                if (v.optionValues == null) return false;
            }
        }

        // Validate bundles and components
        if (bundles != null) {
            for (Bundle b : bundles) {
                if (b == null) return false;
                if (b.kitId != null && b.kitId.isBlank()) return false;
                if (b.components != null) {
                    for (Bundle.Component c : b.components) {
                        if (c == null) return false;
                        if (c.qty == null || c.qty < 0) return false;
                        if (c.sku == null || c.sku.isBlank()) return false;
                    }
                }
            }
        }

        // Validate inventory lots and nodes quantities
        if (inventory != null) {
            if (inventory.lots != null) {
                for (Inventory.Lot lot : inventory.lots) {
                    if (lot == null) return false;
                    if (lot.lotId == null || lot.lotId.isBlank()) return false;
                    if (lot.quantity == null || lot.quantity < 0) return false;
                }
            }
            if (inventory.nodes != null) {
                for (Inventory.Node node : inventory.nodes) {
                    if (node == null) return false;
                    if (node.nodeId == null || node.nodeId.isBlank()) return false;
                    if (node.quantity == null || node.quantity < 0) return false;
                }
            }
        }

        return true;
    }

    @Data
    public static class Attributes {
        private String brand;
        private Map<String, Object> custom;
        private Dimensions dimensions;
        private List<String> hazards;
        private String model;
        private Double weight;
    }

    @Data
    public static class Dimensions {
        private Double height;
        private Double length;
        private Double width;
    }

    @Data
    public static class Bundle {
        private List<Component> components;
        private String kitId;

        @Data
        public static class Component {
            private Integer qty;
            private String sku;
        }
    }

    @Data
    public static class Compliance {
        private List<Doc> docs;
        private List<String> restrictions;

        @Data
        public static class Doc {
            private String docType;
            private String url;
        }
    }

    @Data
    public static class Event {
        private String eventType;
        private String timestamp;
    }

    @Data
    public static class Inventory {
        private List<Lot> lots;
        private List<Node> nodes;
        private Policies policies;
        private List<Reservation> reservations;

        @Data
        public static class Lot {
            private String lotId;
            private Integer quantity;
        }

        @Data
        public static class Node {
            private String nodeId;
            private Integer quantity;
        }

        @Data
        public static class Policies {
            private Boolean allowBackorder;
        }

        @Data
        public static class Reservation {
            private Integer qty;
            private String reservationId;
        }
    }

    @Data
    public static class Localizations {
        private List<LocalizationContent> content;
        private String defaultLocale;

        @Data
        public static class LocalizationContent {
            private String description;
            private String locale;
            private String name;
        }
    }

    @Data
    public static class Options {
        private List<String> axes;
        private Constraints constraints;

        @Data
        public static class Constraints {
            private Integer maxQuantityPerOrder;
        }
    }

    @Data
    public static class Relationships {
        private List<String> relatedProducts;
        private List<String> suppliers;
    }

    @Data
    public static class Variant {
        private Map<String, String> optionValues;
        private Map<String, Object> overrides;
        private String variantSku;
    }
}