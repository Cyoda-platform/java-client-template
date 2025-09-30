package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product entity for OMS with complete schema as specified in requirements
 * Includes all fields: attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, events
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // unique business identifier
    private String name;
    private String description;
    private Double price; // default/base price (fallback)
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
               description != null &&
               price != null && price >= 0 &&
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
        private Map<String, Object> custom; // open extension bag for teams
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
        private Map<String, Object> regulatory; // e.g., {"ukca": true, "ce": true}
        private List<String> salesRestrictions; // e.g., ["noLithiumBatteries"]
    }

    /**
     * Product media including images, documents, etc.
     */
    @Data
    public static class ProductMedia {
        private String type; // "image", "doc", etc.
        private String url;
        private String alt; // for images
        private String title; // for documents
        private List<String> tags; // e.g., ["hero"]
        private List<String> regionScope; // e.g., ["EU"]
        private String sha256; // checksum
    }

    /**
     * Product options with axes and constraints
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
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    /**
     * Product variants with different option combinations
     */
    @Data
    public static class ProductVariant {
        private String variantSku; // unique within product
        private Map<String, String> optionValues; // e.g., {"color": "black", "capacity": "256GB"}
        private ProductAttributes attributes; // overrides
        private List<String> barcodes; // e.g., ["EAN:...", "UPC:..."]
        private ProductPriceOverrides priceOverrides;
        private ProductInventoryPolicy inventoryPolicy;
    }

    @Data
    public static class ProductPriceOverrides {
        private Double base; // optional override of product.price
        private List<String> priceBooks; // e.g., ["pb:consumer-eu-tiered-2025"]
    }

    @Data
    public static class ProductInventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    /**
     * Product bundles (kits or virtual bundles)
     */
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

    /**
     * Product inventory with nodes, lots, reservations, in-transit items
     */
    @Data
    public static class ProductInventory {
        private List<ProductInventoryNode> nodes;
        private ProductInventoryPolicies policies;
    }

    @Data
    public static class ProductInventoryNode {
        private String nodeId; // e.g., "LON-01"
        private String type; // "Warehouse", "3PL"
        private ProductNodeCapacity capacity;
        private List<ProductLot> lots;
        private List<ProductReservation> reservations;
        private List<ProductInTransit> inTransit;
        private Integer qtyOnHand; // for 3PL nodes
    }

    @Data
    public static class ProductNodeCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class ProductLot {
        private String lotId;
        private String mfgDate; // manufacturing date
        private String expires; // expiry date
        private Integer qty;
        private List<String> serials;
        private String quality; // "Released", "Quarantine"
        private String reason; // for quarantine
    }

    @Data
    public static class ProductReservation {
        private String ref; // e.g., "order:O-12345"
        private String variantSku;
        private Integer qty;
        private String until; // ISO datetime
    }

    @Data
    public static class ProductInTransit {
        private String po; // purchase order
        private String eta; // estimated time of arrival
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

    /**
     * Product compliance with documents and restrictions
     */
    @Data
    public static class ProductCompliance {
        private List<ProductComplianceDoc> docs;
        private List<ProductRestriction> restrictions;
    }

    @Data
    public static class ProductComplianceDoc {
        private String id; // e.g., "MSDS-2025-01"
        private List<String> regions; // e.g., ["EU", "US"]
        private String url;
        private Boolean approved;
    }

    @Data
    public static class ProductRestriction {
        private String region; // e.g., "CA"
        private List<String> rules; // e.g., ["noAirTransport"]
        private String reason; // e.g., "Lithium content"
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
        private String partyId; // e.g., "SUP-FOXLINK"
        private ProductContract contract;
    }

    @Data
    public static class ProductContract {
        private String id; // e.g., "C-2025-07"
        private String incoterm; // e.g., "DAP"
        private Integer leadTimeDays;
    }

    @Data
    public static class ProductRelatedProduct {
        private String type; // "accessory", "replacement"
        private String sku;
    }

    /**
     * Product events for audit trail
     */
    @Data
    public static class ProductEvent {
        private String type; // e.g., "ProductCreated", "InventoryReceived"
        private String at; // ISO datetime
        private Map<String, Object> payload;
    }
}
