package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product entity implementing the complete product schema as specified in functional requirements.
 * This entity represents a product in the catalog with full e-commerce capabilities including
 * variants, bundles, inventory, compliance, and relationships.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // Business ID - unique identifier
    private String name;
    private String description;
    private Double price; // Base/default price
    private Integer quantityAvailable; // Quick projection field
    private String category;
    
    // Optional core fields
    private String warehouseId; // Default primary node

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

    @Data
    public static class ProductAttributes {
        private String brand;
        private String model;
        private ProductDimensions dimensions;
        private ProductWeight weight;
        private List<ProductHazard> hazards;
        private Map<String, Object> custom; // Open extension bag
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

    @Data
    public static class ProductLocalizations {
        private String defaultLocale;
        private List<ProductLocalizationContent> content;
    }

    @Data
    public static class ProductLocalizationContent {
        private String locale;
        private String name;
        private String description;
        private Map<String, Object> regulatory; // e.g., {"ukca": true, "ce": true}
        private List<String> salesRestrictions; // e.g., ["noLithiumBatteries"]
    }

    @Data
    public static class ProductMedia {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt;
        private String title;
        private List<String> tags; // e.g., ["hero"]
        private List<String> regionScope; // e.g., ["EU"]
        private String sha256;
    }

    @Data
    public static class ProductOptions {
        private List<ProductOptionAxis> axes;
        private List<ProductOptionConstraint> constraints;
    }

    @Data
    public static class ProductOptionAxis {
        private String code; // e.g., "color"
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

    @Data
    public static class ProductVariant {
        private String variantSku;
        private Map<String, String> optionValues; // e.g., {"color": "black", "capacity": "256GB"}
        private ProductAttributes attributes; // Overrides
        private List<String> barcodes; // e.g., ["EAN:...", "UPC:..."]
        private ProductPriceOverrides priceOverrides;
        private ProductInventoryPolicy inventoryPolicy;
    }

    @Data
    public static class ProductPriceOverrides {
        private Double base; // Optional override of product.price
        private List<String> priceBooks;
    }

    @Data
    public static class ProductInventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    @Data
    public static class ProductBundle {
        private String type; // "kit" or "bundle"
        private String sku;
        private List<ProductBundleComponent> components;
    }

    @Data
    public static class ProductBundleComponent {
        private ProductBundleRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ProductBundleConstraint> constraints;
        private List<ProductBundleSubstitution> substitutions;
    }

    @Data
    public static class ProductBundleRef {
        private String sku;
    }

    @Data
    public static class ProductBundleConstraint {
        private Map<String, Object> ifVariant;
        private ProductBundleConstraintThen then;
    }

    @Data
    public static class ProductBundleConstraintThen {
        private Boolean forbid;
    }

    @Data
    public static class ProductBundleSubstitution {
        private String sku;
        private List<String> whenRegionIn;
    }

    @Data
    public static class ProductInventory {
        private List<ProductInventoryNode> nodes;
        private ProductInventoryPolicies policies;
    }

    @Data
    public static class ProductInventoryNode {
        private String nodeId;
        private String type; // "Warehouse", "3PL"
        private ProductInventoryCapacity capacity;
        private Integer qtyOnHand; // For simple nodes
        private List<ProductInventoryLot> lots;
        private List<ProductInventoryReservation> reservations;
        private List<ProductInventoryInTransit> inTransit;
    }

    @Data
    public static class ProductInventoryCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class ProductInventoryLot {
        private String lotId;
        private LocalDateTime mfgDate;
        private LocalDateTime expires;
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine"
        private String reason; // For quarantine
    }

    @Data
    public static class ProductInventoryReservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private LocalDateTime until;
    }

    @Data
    public static class ProductInventoryInTransit {
        private String po; // Purchase order
        private LocalDateTime eta;
        private Integer qty;
        private String status; // "Scheduled"
    }

    @Data
    public static class ProductInventoryPolicies {
        private String allocation; // e.g., "earliest-expiry-first"
        private ProductOversellGuard oversellGuard;
    }

    @Data
    public static class ProductOversellGuard {
        private Integer maxPercent;
    }

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
        private String type; // "accessory", "replacement"
        private String sku;
    }

    @Data
    public static class ProductEvent {
        private String type; // "ProductCreated", "InventoryReceived", etc.
        private LocalDateTime at;
        private Map<String, Object> payload;
    }
}
