package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Entity - Represents a product in the catalog with complete schema for persistence and round-trip operations.
 * 
 * This entity implements the full Product schema as specified in the user requirements,
 * including all complex nested structures for attributes, localizations, media, options,
 * variants, bundles, inventory, compliance, relationships, and events.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku;                    // Required, unique
    private String name;                   // Required
    private String description;            // Required
    private Double price;                  // Required: default/base price (fallback)
    private Integer quantityAvailable;     // Required: quick projection field
    private String category;               // Required

    // Optional fields
    private String warehouseId;            // Optional: default primary warehouse node

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

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
               price != null && price > 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    /**
     * Product attributes including brand, model, dimensions, weight, hazards, custom fields
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
        private Double l;    // length
        private Double w;    // width
        private Double h;    // height
        private String unit; // e.g., "cm"
    }

    @Data
    public static class ProductWeight {
        private Double value;
        private String unit; // e.g., "kg"
    }

    @Data
    public static class ProductHazard {
        private String clazz;           // "class" is reserved keyword, using "clazz"
        private String transportNotes;
    }

    /**
     * Multi-language content with locale-specific names, descriptions, and regulatory info
     */
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

    /**
     * Product media files (images, documents) with metadata
     */
    @Data
    public static class ProductMedia {
        private String type;        // "image", "doc"
        private String url;
        private String alt;
        private String title;
        private List<String> tags;
        private List<String> regionScope;
        private String sha256;
    }

    /**
     * Product option axes and constraints for variants
     */
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
        private Map<String, Object> ifCondition;    // "if" is reserved keyword
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    /**
     * Product variants with option values, attributes, barcodes, price overrides
     */
    @Data
    public static class ProductVariant {
        private String variantSku;
        private Map<String, String> optionValues;
        private ProductAttributes attributes;  // Overrides
        private List<String> barcodes;
        private ProductPriceOverrides priceOverrides;
        private ProductInventoryPolicy inventoryPolicy;
    }

    @Data
    public static class ProductPriceOverrides {
        private Double base;
        private List<String> priceBooks;
    }

    @Data
    public static class ProductInventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    /**
     * Product bundles and kits with components
     */
    @Data
    public static class ProductBundle {
        private String type;        // "kit" or "bundle"
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

    /**
     * Detailed inventory information across nodes, lots, reservations
     */
    @Data
    public static class ProductInventory {
        private List<ProductInventoryNode> nodes;
        private ProductInventoryPolicies policies;
    }

    @Data
    public static class ProductInventoryNode {
        private String nodeId;
        private String type;
        private ProductInventoryCapacity capacity;
        private List<ProductInventoryLot> lots;
        private List<ProductInventoryReservation> reservations;
        private List<ProductInventoryInTransit> inTransit;
        private Integer qtyOnHand;
    }

    @Data
    public static class ProductInventoryCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class ProductInventoryLot {
        private String lotId;
        private String mfgDate;
        private String expires;
        private Integer qty;
        private List<String> serials;
        private String quality;
        private String reason;
    }

    @Data
    public static class ProductInventoryReservation {
        private String ref;
        private String variantSku;
        private Integer qty;
        private String until;
    }

    @Data
    public static class ProductInventoryInTransit {
        private String po;
        private String eta;
        private Integer qty;
        private String status;
    }

    @Data
    public static class ProductInventoryPolicies {
        private String allocation;
        private ProductOversellGuard oversellGuard;
    }

    @Data
    public static class ProductOversellGuard {
        private Integer maxPercent;
    }

    /**
     * Compliance documents and regional restrictions
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
     * Supplier relationships and related products
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
        private String type;
        private String sku;
    }

    /**
     * Product lifecycle events
     */
    @Data
    public static class ProductEvent {
        private String type;
        private String at;
        private Map<String, Object> payload;
    }
}
