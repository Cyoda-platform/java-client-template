package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: Product entity representing catalog items with comprehensive schema
 * including attributes, localizations, media, options, variants, bundles, inventory, compliance, and relationships.
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
    
    // Optional fields
    private String warehouseId; // optional default primary node
    
    // Complex nested structures
    private ProductAttributes attributes;
    private ProductLocalizations localizations;
    private List<ProductMedia> media;
    private ProductOptions options;
    private List<ProductVariant> variants;
    private List<ProductBundle> bundles;
    private ProductInventory inventory;
    private ProductCompliance compliance;
    private ProductRelationships relationships;
    private List<ProductEvent> events;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
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
    public static class ProductAttributes {
        private String brand;
        private String model;
        private ProductDimensions dimensions;
        private ProductWeight weight;
        private List<ProductHazard> hazards;
        private Map<String, Object> custom; // open extension bag for teams
    }

    /**
     * Product dimensions with length, width, height, and unit
     */
    @Data
    public static class ProductDimensions {
        private Double l; // length
        private Double w; // width
        private Double h; // height
        private String unit; // e.g., "cm"
    }

    /**
     * Product weight with value and unit
     */
    @Data
    public static class ProductWeight {
        private Double value;
        private String unit; // e.g., "kg"
    }

    /**
     * Product hazard information for shipping and handling
     */
    @Data
    public static class ProductHazard {
        private String clazz; // "class" is reserved keyword, using "clazz"
        private String transportNotes;
    }

    /**
     * Product localizations for different markets and languages
     */
    @Data
    public static class ProductLocalizations {
        private String defaultLocale; // e.g., "en-GB"
        private List<ProductLocalizationContent> content;
    }

    /**
     * Localized content for specific locale
     */
    @Data
    public static class ProductLocalizationContent {
        private String locale; // e.g., "en-GB", "de-DE"
        private String name;
        private String description;
        private ProductRegulatory regulatory;
        private List<String> salesRestrictions;
    }

    /**
     * Regulatory information for specific locale
     */
    @Data
    public static class ProductRegulatory {
        private Boolean ukca;
        private Boolean ce;
    }

    /**
     * Product media including images, documents, and other assets
     */
    @Data
    public static class ProductMedia {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt; // for images
        private String title; // for documents
        private List<String> tags; // e.g., ["hero"]
        private List<String> regionScope; // e.g., ["EU"]
        private String sha256; // content hash
    }

    /**
     * Product options and constraints for variants
     */
    @Data
    public static class ProductOptions {
        private List<ProductOptionAxis> axes;
        private List<ProductOptionConstraint> constraints;
    }

    /**
     * Option axis definition (e.g., color, capacity)
     */
    @Data
    public static class ProductOptionAxis {
        private String code; // e.g., "color", "capacity"
        private List<String> values; // e.g., ["black","silver","blue"]
    }

    /**
     * Option constraints for variant combinations
     */
    @Data
    public static class ProductOptionConstraint {
        private Map<String, String> ifCondition; // "if" is reserved keyword
        private ProductOptionConstraintThen then;
        private List<ProductOptionRequirement> requires;
        private List<String> whenRegionIn;
    }

    /**
     * Constraint "then" clause
     */
    @Data
    public static class ProductOptionConstraintThen {
        private Map<String, List<String>> forbid;
    }

    /**
     * Option requirement
     */
    @Data
    public static class ProductOptionRequirement {
        private String option;
        private List<String> oneOf;
    }

    /**
     * Product variant with specific option values and overrides
     */
    @Data
    public static class ProductVariant {
        private String variantSku; // unique within product
        private Map<String, String> optionValues; // e.g., {"color": "black", "capacity": "256GB"}
        private ProductAttributes attributes; // overrides
        private List<String> barcodes; // ["EAN:...","UPC:..."]
        private ProductVariantPriceOverrides priceOverrides;
        private ProductInventoryPolicy inventoryPolicy;
    }

    /**
     * Variant price overrides
     */
    @Data
    public static class ProductVariantPriceOverrides {
        private Double base; // optional override of product.price
        private List<String> priceBooks;
    }

    /**
     * Inventory policy for variant
     */
    @Data
    public static class ProductInventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    /**
     * Product bundle definition
     */
    @Data
    public static class ProductBundle {
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<ProductBundleComponent> components;
    }

    /**
     * Bundle component definition
     */
    @Data
    public static class ProductBundleComponent {
        private ProductBundleRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ProductBundleConstraint> constraints;
        private List<ProductBundleSubstitution> substitutions;
    }

    /**
     * Bundle component reference
     */
    @Data
    public static class ProductBundleRef {
        private String sku;
    }

    /**
     * Bundle component constraint
     */
    @Data
    public static class ProductBundleConstraint {
        private ProductBundleConstraintIf ifVariant;
        private ProductBundleConstraintThen then;
    }

    /**
     * Bundle constraint "if" condition
     */
    @Data
    public static class ProductBundleConstraintIf {
        private String option;
        private List<String> in;
    }

    /**
     * Bundle constraint "then" action
     */
    @Data
    public static class ProductBundleConstraintThen {
        private Boolean forbid;
    }

    /**
     * Bundle component substitution
     */
    @Data
    public static class ProductBundleSubstitution {
        private String sku;
        private List<String> whenRegionIn;
    }

    /**
     * Product inventory information across nodes
     */
    @Data
    public static class ProductInventory {
        private List<ProductInventoryNode> nodes;
        private ProductInventoryPolicies policies;
    }

    /**
     * Inventory node (warehouse, 3PL, etc.)
     */
    @Data
    public static class ProductInventoryNode {
        private String nodeId;
        private String type; // "Warehouse", "3PL"
        private ProductInventoryCapacity capacity;
        private List<ProductInventoryLot> lots;
        private List<ProductInventoryReservation> reservations;
        private List<ProductInventoryInTransit> inTransit;
        private Integer qtyOnHand; // for 3PL nodes
    }

    /**
     * Inventory capacity information
     */
    @Data
    public static class ProductInventoryCapacity {
        private Integer maxUnits;
    }

    /**
     * Inventory lot information
     */
    @Data
    public static class ProductInventoryLot {
        private String lotId;
        private LocalDateTime mfgDate;
        private LocalDateTime expires;
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine"
        private String reason; // for quarantine
    }

    /**
     * Inventory reservation
     */
    @Data
    public static class ProductInventoryReservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private LocalDateTime until;
    }

    /**
     * Inventory in transit
     */
    @Data
    public static class ProductInventoryInTransit {
        private String po; // purchase order
        private LocalDateTime eta;
        private Integer qty;
        private String status; // "Scheduled"
    }

    /**
     * Inventory policies
     */
    @Data
    public static class ProductInventoryPolicies {
        private String allocation; // "earliest-expiry-first"
        private ProductOversellGuard oversellGuard;
    }

    /**
     * Oversell guard configuration
     */
    @Data
    public static class ProductOversellGuard {
        private Integer maxPercent;
    }

    /**
     * Product compliance information
     */
    @Data
    public static class ProductCompliance {
        private List<ProductComplianceDoc> docs;
        private List<ProductComplianceRestriction> restrictions;
    }

    /**
     * Compliance document
     */
    @Data
    public static class ProductComplianceDoc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

    /**
     * Compliance restriction
     */
    @Data
    public static class ProductComplianceRestriction {
        private String region;
        private List<String> rules;
        private String reason;
    }

    /**
     * Product relationships with suppliers and related products
     */
    @Data
    public static class ProductRelationships {
        private List<ProductSupplier> suppliers;
        private List<ProductRelatedProduct> relatedProducts;
    }

    /**
     * Product supplier information
     */
    @Data
    public static class ProductSupplier {
        private String partyId;
        private ProductSupplierContract contract;
    }

    /**
     * Supplier contract information
     */
    @Data
    public static class ProductSupplierContract {
        private String id;
        private String incoterm;
        private Integer leadTimeDays;
    }

    /**
     * Related product information
     */
    @Data
    public static class ProductRelatedProduct {
        private String type; // "accessory", "replacement"
        private String sku;
    }

    /**
     * Product event for audit trail
     */
    @Data
    public static class ProductEvent {
        private String type; // "ProductCreated", "InventoryReceived", etc.
        private LocalDateTime at;
        private Map<String, Object> payload;
    }
}
