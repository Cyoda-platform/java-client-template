package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;
import java.util.Map;

/**
 * Product entity with complete schema support for OMS catalog management
 * Implements the full Product schema as specified in functional requirements
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // unique business identifier
    private String name;
    private String description;
    private Double price; // default/base price
    private Integer quantityAvailable; // quick projection field
    private String category;
    
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
    public boolean isValid() {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               price != null && price >= 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    // Nested classes for complex structures

    @Data
    public static class ProductAttributes {
        private String brand;
        private String model;
        private ProductDimensions dimensions;
        private ProductWeight weight;
        private List<ProductHazard> hazards;
        private Map<String, Object> custom; // open extension bag
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
        private Map<String, Object> regulatory;
        private List<String> salesRestrictions;
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
        private String variantSku;
        private Map<String, String> optionValues;
        private ProductAttributes attributes; // overrides
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
        private Map<String, Object> then;
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
        private String type; // "accessory", "replacement", etc.
        private String sku;
    }

    @Data
    public static class ProductEvent {
        private String type;
        private String at;
        private Map<String, Object> payload;
    }
}
