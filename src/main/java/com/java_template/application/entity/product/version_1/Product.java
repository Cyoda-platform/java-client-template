package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: Product entity representing the complete product catalog schema
 * with all complex nested structures for inventory, compliance, and relationships.
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

    /**
     * Product attributes including brand, model, dimensions, weight, hazards, and custom fields
     */
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
        private Double l; // length
        private Double w; // width
        private Double h; // height
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

    /**
     * Localization support for multiple regions and languages
     */
    @Data
    public static class Localizations {
        private String defaultLocale; // e.g., "en-GB"
        private List<LocalizationContent> content;
    }

    @Data
    public static class LocalizationContent {
        private String locale; // e.g., "en-GB", "de-DE"
        private String name;
        private String description;
        private Regulatory regulatory;
        private List<String> salesRestrictions;
    }

    @Data
    public static class Regulatory {
        private Boolean ukca;
        private Boolean ce;
    }

    /**
     * Media files including images, documents, and other assets
     */
    @Data
    public static class Media {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt; // for images
        private String title; // for documents
        private List<String> tags; // e.g., ["hero"]
        private List<String> regionScope; // e.g., ["EU"]
        private String sha256; // file hash
    }

    /**
     * Product options and variants configuration
     */
    @Data
    public static class Options {
        private List<OptionAxis> axes;
        private List<OptionConstraint> constraints;
    }

    @Data
    public static class OptionAxis {
        private String code; // e.g., "color", "capacity"
        private List<String> values; // e.g., ["black", "silver", "blue"]
    }

    @Data
    public static class OptionConstraint {
        private Map<String, String> ifCondition; // "if" is reserved, using "ifCondition"
        private ForbidConstraint then;
        private List<RequirementConstraint> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class ForbidConstraint {
        private Map<String, List<String>> forbid;
    }

    @Data
    public static class RequirementConstraint {
        private String option;
        private List<String> oneOf;
    }

    /**
     * Product variants with specific option combinations
     */
    @Data
    public static class Variant {
        private String variantSku; // unique within product
        private Map<String, String> optionValues; // e.g., {"color": "black", "capacity": "256GB"}
        private Attributes attributes; // overrides
        private List<String> barcodes; // ["EAN:...", "UPC:..."]
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

    /**
     * Product bundles and kits
     */
    @Data
    public static class Bundle {
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<BundleComponent> components;
    }

    @Data
    public static class BundleComponent {
        private ProductReference ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<BundleConstraint> constraints;
        private List<Substitution> substitutions;
    }

    @Data
    public static class ProductReference {
        private String sku;
    }

    @Data
    public static class BundleConstraint {
        private VariantCondition ifVariant;
        private ForbidAction then;
    }

    @Data
    public static class VariantCondition {
        private String option;
        private List<String> in;
    }

    @Data
    public static class ForbidAction {
        private Boolean forbid;
    }

    @Data
    public static class Substitution {
        private String sku;
        private List<String> whenRegionIn;
    }

    /**
     * Inventory management across multiple nodes
     */
    @Data
    public static class Inventory {
        private List<InventoryNode> nodes;
        private InventoryPolicies policies;
    }

    @Data
    public static class InventoryNode {
        private String nodeId;
        private String type; // "Warehouse", "3PL"
        private NodeCapacity capacity;
        private List<InventoryLot> lots;
        private List<Reservation> reservations;
        private List<InTransit> inTransit;
        private Integer qtyOnHand; // for simple nodes
    }

    @Data
    public static class NodeCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class InventoryLot {
        private String lotId;
        private String mfgDate; // manufacturing date
        private String expires; // expiration date
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine"
        private String reason; // for quarantine
    }

    @Data
    public static class Reservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private String until; // reservation expiry
    }

    @Data
    public static class InTransit {
        private String po; // purchase order
        private String eta; // estimated time of arrival
        private Integer qty;
        private String status; // "Scheduled"
    }

    @Data
    public static class InventoryPolicies {
        private String allocation; // "earliest-expiry-first"
        private OversellGuard oversellGuard;
    }

    @Data
    public static class OversellGuard {
        private Integer maxPercent;
    }

    /**
     * Compliance documentation and restrictions
     */
    @Data
    public static class Compliance {
        private List<ComplianceDoc> docs;
        private List<Restriction> restrictions;
    }

    @Data
    public static class ComplianceDoc {
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

    /**
     * Supplier and product relationships
     */
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
        private String type; // "accessory", "replacement"
        private String sku;
    }

    /**
     * Product lifecycle events
     */
    @Data
    public static class Event {
        private String type; // "ProductCreated", "InventoryReceived", "ReservationCreated"
        private String at; // timestamp
        private Map<String, Object> payload;
    }
}
