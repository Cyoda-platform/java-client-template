package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product entity with full schema as specified in requirements
 * Includes attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // required, unique business identifier
    private String name; // required
    private String description; // required
    private Double price; // required: default/base price (fallback)
    private Integer quantityAvailable; // required: quick projection field
    private String category; // required
    
    // Optional core fields
    private String warehouseId; // optional default primary node

    // Complex nested structures
    private Attributes attributes;
    private Localizations localizations;
    private List<Media> media;
    private Options options;
    private List<Variant> variants;
    private List<Bundle> bundles;
    private Inventory inventory;
    private Compliance compliance;
    private Relationships relationships;
    private List<Event> events;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               price != null && price >= 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    // Nested classes for complex structures

    @Data
    public static class Attributes {
        private String brand;
        private String model;
        private Dimensions dimensions;
        private Weight weight;
        private List<Hazard> hazards;
        private Map<String, Object> custom; // open extension bag for teams
    }

    @Data
    public static class Dimensions {
        private Double l;
        private Double w;
        private Double h;
        private String unit; // e.g., "cm"
    }

    @Data
    public static class Weight {
        private Double value;
        private String unit; // e.g., "kg"
    }

    @Data
    public static class Hazard {
        private String clazz; // "class" is reserved keyword, using "clazz"
        private String transportNotes;
    }

    @Data
    public static class Localizations {
        private String defaultLocale;
        private List<LocalizationContent> content;
    }

    @Data
    public static class LocalizationContent {
        private String locale;
        private String name;
        private String description;
        private Map<String, Object> regulatory;
        private List<String> salesRestrictions;
    }

    @Data
    public static class Media {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt;
        private String title;
        private List<String> tags;
        private List<String> regionScope;
        private String sha256;
    }

    @Data
    public static class Options {
        private List<Axis> axes;
        private List<Constraint> constraints;
    }

    @Data
    public static class Axis {
        private String code;
        private List<String> values;
    }

    @Data
    public static class Constraint {
        private Map<String, Object> ifCondition; // "if" is reserved keyword
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class Variant {
        private String variantSku; // unique within product
        private Map<String, String> optionValues;
        private Attributes attributes; // overrides
        private List<String> barcodes;
        private PriceOverrides priceOverrides;
        private InventoryPolicy inventoryPolicy;
    }

    @Data
    public static class PriceOverrides {
        private Double base; // optional override of product.price
        private List<String> priceBooks;
    }

    @Data
    public static class InventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    @Data
    public static class Bundle {
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<Component> components;
    }

    @Data
    public static class Component {
        private Reference ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ComponentConstraint> constraints;
        private List<Substitution> substitutions;
    }

    @Data
    public static class Reference {
        private String sku;
    }

    @Data
    public static class ComponentConstraint {
        private Map<String, Object> ifVariant;
        private Map<String, Object> then;
    }

    @Data
    public static class Substitution {
        private String sku;
        private List<String> whenRegionIn;
    }

    @Data
    public static class Inventory {
        private List<Node> nodes;
        private Policies policies;
    }

    @Data
    public static class Node {
        private String nodeId;
        private String type; // "Warehouse", "3PL", etc.
        private Capacity capacity;
        private List<Lot> lots;
        private List<Reservation> reservations;
        private List<InTransit> inTransit;
        private Integer qtyOnHand; // for simple nodes like 3PL
    }

    @Data
    public static class Capacity {
        private Integer maxUnits;
    }

    @Data
    public static class Lot {
        private String lotId;
        private String mfgDate;
        private String expires;
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine", etc.
        private String reason;
    }

    @Data
    public static class Reservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private String until; // ISO datetime
    }

    @Data
    public static class InTransit {
        private String po;
        private String eta; // ISO datetime
        private Integer qty;
        private String status; // "Scheduled", etc.
    }

    @Data
    public static class Policies {
        private String allocation; // "earliest-expiry-first", etc.
        private OversellGuard oversellGuard;
    }

    @Data
    public static class OversellGuard {
        private Integer maxPercent;
    }

    @Data
    public static class Compliance {
        private List<Doc> docs;
        private List<Restriction> restrictions;
    }

    @Data
    public static class Doc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

    @Data
    public static class Restriction {
        private String region;
        private List<String> rules;
        private String reason;
    }

    @Data
    public static class Relationships {
        private List<Supplier> suppliers;
        private List<RelatedProduct> relatedProducts;
    }

    @Data
    public static class Supplier {
        private String partyId;
        private Contract contract;
    }

    @Data
    public static class Contract {
        private String id;
        private String incoterm;
        private Integer leadTimeDays;
    }

    @Data
    public static class RelatedProduct {
        private String type; // "accessory", "replacement", etc.
        private String sku;
    }

    @Data
    public static class Event {
        private String type;
        private String at; // ISO datetime
        private Map<String, Object> payload;
    }
}
