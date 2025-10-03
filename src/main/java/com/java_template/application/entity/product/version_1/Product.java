package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: Product entity representing catalog items with full schema including
 * attributes, localizations, media, options, variants, bundles, inventory, compliance, and relationships.
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
    
    // Optional fields
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
        private Double l;
        private Double w;
        private Double h;
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
        private ProductRegulatory regulatory;
        private List<String> salesRestrictions;
    }

    @Data
    public static class ProductRegulatory {
        private Boolean ukca;
        private Boolean ce;
    }

    @Data
    public static class ProductMedia {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt;
        private String title;
        private List<String> tags;
        private List<String> regionScope;
        private String sha256;
    }

    @Data
    public static class ProductOptions {
        private List<ProductOptionAxis> axes;
        private List<ProductOptionConstraint> constraints;
    }

    @Data
    public static class ProductOptionAxis {
        private String code;
        private List<String> values;
    }

    @Data
    public static class ProductOptionConstraint {
        private Map<String, Object> ifCondition; // "if" is reserved keyword
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class ProductVariant {
        private String variantSku; // Unique within product
        private Map<String, String> optionValues;
        private ProductAttributes attributes; // Overrides
        private List<String> barcodes;
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
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<ProductBundleComponent> components;
    }

    @Data
    public static class ProductBundleComponent {
        private ProductComponentRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ProductComponentConstraint> constraints;
        private List<ProductComponentSubstitution> substitutions;
    }

    @Data
    public static class ProductComponentRef {
        private String sku;
    }

    @Data
    public static class ProductComponentConstraint {
        private Map<String, Object> ifVariant;
        private Map<String, Object> then;
    }

    @Data
    public static class ProductComponentSubstitution {
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
        private String type; // "Warehouse", "3PL", etc.
        private ProductNodeCapacity capacity;
        private Integer qtyOnHand;
        private List<ProductLot> lots;
        private List<ProductReservation> reservations;
        private List<ProductInTransit> inTransit;
    }

    @Data
    public static class ProductNodeCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class ProductLot {
        private String lotId;
        private LocalDateTime mfgDate;
        private LocalDateTime expires;
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine", etc.
        private String reason;
    }

    @Data
    public static class ProductReservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private LocalDateTime until;
    }

    @Data
    public static class ProductInTransit {
        private String po;
        private LocalDateTime eta;
        private Integer qty;
        private String status; // "Scheduled", etc.
    }

    @Data
    public static class ProductInventoryPolicies {
        private String allocation; // "earliest-expiry-first", etc.
        private ProductOversellGuard oversellGuard;
    }

    @Data
    public static class ProductOversellGuard {
        private Integer maxPercent;
    }

    @Data
    public static class ProductCompliance {
        private List<ProductComplianceDoc> docs;
        private List<ProductRestriction> restrictions;
    }

    @Data
    public static class ProductComplianceDoc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

    @Data
    public static class ProductRestriction {
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
        private ProductContract contract;
    }

    @Data
    public static class ProductContract {
        private String id;
        private String incoterm;
        private Integer leadTimeDays;
    }

    @Data
    public static class ProductRelatedProduct {
        private String type; // "accessory", "replacement", etc.
        private String sku;
    }

    @Data
    public static class ProductEvent {
        private String type; // "ProductCreated", "InventoryReceived", etc.
        private LocalDateTime at;
        private Map<String, Object> payload;
    }
}
