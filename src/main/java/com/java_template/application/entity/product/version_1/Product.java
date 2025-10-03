package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: Product entity representing catalog items with complete schema including
 * attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // Business ID - unique identifier
    private String name;
    private String description;
    private Double price; // Default/base price (fallback)
    private Integer quantityAvailable; // Quick projection field
    private String category;
    
    // Optional core fields
    private String warehouseId; // Optional default primary node

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
    public boolean isValid() {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
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
        private Map<String, Object> custom; // Open extension bag for teams
    }

    @Data
    public static class ProductDimensions {
        private Double l; // length
        private Double w; // width
        private Double h; // height
        private String unit; // e.g., "cm"
    }

    @Data
    public static class ProductWeight {
        private Double value;
        private String unit; // e.g., "kg"
    }

    @Data
    public static class ProductHazard {
        private String clazz; // "class" is reserved keyword, using "clazz"
        private String transportNotes;
    }

    /**
     * Product localizations with default locale and content for different regions
     */
    @Data
    public static class ProductLocalizations {
        private String defaultLocale; // e.g., "en-GB"
        private List<ProductLocalizationContent> content;
    }

    @Data
    public static class ProductLocalizationContent {
        private String locale; // e.g., "en-GB", "de-DE"
        private String name;
        private String description;
        private Map<String, Boolean> regulatory; // e.g., {"ukca": true, "ce": true}
        private List<String> salesRestrictions; // e.g., ["noLithiumBatteries"]
    }

    /**
     * Product media including images, documents, and other assets
     */
    @Data
    public static class ProductMedia {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt; // Alt text for images
        private String title; // Title for documents
        private List<String> tags; // e.g., ["hero"]
        private List<String> regionScope; // e.g., ["EU"]
        private String sha256; // File hash
    }

    /**
     * Product options with axes and constraints for variants
     */
    @Data
    public static class ProductOptions {
        private List<ProductOptionAxis> axes;
        private List<ProductOptionConstraint> constraints;
    }

    @Data
    public static class ProductOptionAxis {
        private String code; // e.g., "color", "capacity"
        private List<String> values; // e.g., ["black", "silver", "blue"]
    }

    @Data
    public static class ProductOptionConstraint {
        private Map<String, String> ifCondition; // "if" is reserved, using "ifCondition"
        private ProductOptionConstraintThen then;
        private List<ProductOptionRequirement> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class ProductOptionConstraintThen {
        private Map<String, List<String>> forbid;
    }

    @Data
    public static class ProductOptionRequirement {
        private String option;
        private List<String> oneOf;
    }

    /**
     * Product variants with specific option combinations and overrides
     */
    @Data
    public static class ProductVariant {
        private String variantSku; // Unique within product
        private Map<String, String> optionValues; // e.g., {"color": "black", "capacity": "256GB"}
        private ProductAttributes attributes; // Overrides for variant-specific attributes
        private List<String> barcodes; // e.g., ["EAN:...", "UPC:..."]
        private ProductVariantPriceOverrides priceOverrides;
        private ProductVariantInventoryPolicy inventoryPolicy;
    }

    @Data
    public static class ProductVariantPriceOverrides {
        private Double base; // Optional override of product.price
        private List<String> priceBooks; // e.g., ["pb:consumer-eu-tiered-2025"]
    }

    @Data
    public static class ProductVariantInventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    /**
     * Product bundles for kits and virtual bundles
     */
    @Data
    public static class ProductBundle {
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<ProductBundleComponent> components;
    }

    @Data
    public static class ProductBundleComponent {
        private ProductBundleComponentRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ProductBundleComponentConstraint> constraints;
        private List<ProductBundleComponentSubstitution> substitutions;
    }

    @Data
    public static class ProductBundleComponentRef {
        private String sku;
    }

    @Data
    public static class ProductBundleComponentConstraint {
        private Map<String, Object> ifVariant;
        private ProductBundleComponentConstraintThen then;
    }

    @Data
    public static class ProductBundleComponentConstraintThen {
        private Boolean forbid;
    }

    @Data
    public static class ProductBundleComponentSubstitution {
        private String sku;
        private List<String> whenRegionIn;
    }

    /**
     * Product inventory with nodes, lots, reservations, and policies
     */
    @Data
    public static class ProductInventory {
        private List<ProductInventoryNode> nodes;
        private ProductInventoryPolicies policies;
    }

    @Data
    public static class ProductInventoryNode {
        private String nodeId;
        private String type; // "Warehouse", "3PL", etc.
        private ProductInventoryCapacity capacity;
        private List<ProductInventoryLot> lots;
        private List<ProductInventoryReservation> reservations;
        private List<ProductInventoryInTransit> inTransit;
        private Integer qtyOnHand; // For simple nodes like 3PL
    }

    @Data
    public static class ProductInventoryCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class ProductInventoryLot {
        private String lotId;
        private String mfgDate; // Manufacturing date
        private String expires; // Expiry date
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine", etc.
        private String reason; // For quarantine
    }

    @Data
    public static class ProductInventoryReservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private String until; // ISO datetime
    }

    @Data
    public static class ProductInventoryInTransit {
        private String po; // Purchase order
        private String eta; // Estimated time of arrival
        private Integer qty;
        private String status; // "Scheduled", etc.
    }

    @Data
    public static class ProductInventoryPolicies {
        private String allocation; // "earliest-expiry-first", etc.
        private ProductInventoryOversellGuard oversellGuard;
    }

    @Data
    public static class ProductInventoryOversellGuard {
        private Integer maxPercent;
    }

    /**
     * Product compliance with documents and restrictions
     */
    @Data
    public static class ProductCompliance {
        private List<ProductComplianceDoc> docs;
        private List<ProductComplianceRestriction> restrictions;
    }

    @Data
    public static class ProductComplianceDoc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

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

    @Data
    public static class ProductSupplier {
        private String partyId;
        private ProductSupplierContract contract;
    }

    @Data
    public static class ProductSupplierContract {
        private String id;
        private String incoterm;
        private Integer leadTimeDays;
    }

    @Data
    public static class ProductRelatedProduct {
        private String type; // "accessory", "replacement", etc.
        private String sku;
    }

    /**
     * Product events for audit trail and history
     */
    @Data
    public static class ProductEvent {
        private String type; // "ProductCreated", "InventoryReceived", etc.
        private String at; // ISO datetime
        private Map<String, Object> payload;
    }
}
