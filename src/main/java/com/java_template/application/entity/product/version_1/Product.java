package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Entity - Complete OMS Product with full schema
 * 
 * Represents a product in the Order Management System with comprehensive
 * attributes including inventory, variants, bundles, compliance, and relationships.
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
               description != null &&
               price != null && price >= 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    /**
     * Product attributes including brand, model, dimensions, weight, hazards
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
        private String unit; // cm
    }

    @Data
    public static class Weight {
        private Double value;
        private String unit; // kg
    }

    @Data
    public static class Hazard {
        private String clazz; // "class" is reserved keyword, using "clazz"
        private String transportNotes;
    }

    /**
     * Localization support for multiple regions
     */
    @Data
    public static class Localizations {
        private String defaultLocale; // en-GB
        private List<LocalizationContent> content;
    }

    @Data
    public static class LocalizationContent {
        private String locale; // en-GB, de-DE
        private String name;
        private String description;
        private Map<String, Boolean> regulatory; // ukca: true, ce: true
        private List<String> salesRestrictions; // noLithiumBatteries
    }

    /**
     * Media files including images and documents
     */
    @Data
    public static class Media {
        private String type; // image, doc
        private String url;
        private String alt;
        private String title;
        private List<String> tags; // hero
        private List<String> regionScope; // EU
        private String sha256;
    }

    /**
     * Product options and constraints (variants configuration)
     */
    @Data
    public static class Options {
        private List<OptionAxis> axes;
        private List<OptionConstraint> constraints;
    }

    @Data
    public static class OptionAxis {
        private String code; // color, capacity
        private List<String> values; // black, silver, blue
    }

    @Data
    public static class OptionConstraint {
        private Map<String, String> ifCondition; // if: { color: "blue" }
        private Map<String, Map<String, List<String>>> then; // then: { forbid: { capacity: ["512GB"] } }
        private List<Map<String, Object>> requires; // requires: [{ option: "capacity", oneOf: ["256GB","512GB"] }]
        private List<String> whenRegionIn; // US, CA
    }

    /**
     * Product variants with specific option combinations
     */
    @Data
    public static class Variant {
        private String variantSku; // unique within product
        private Map<String, String> optionValues; // color: "black", capacity: "256GB"
        private Attributes attributes; // overrides
        private List<String> barcodes; // EAN:..., UPC:...
        private PriceOverrides priceOverrides;
        private InventoryPolicy inventoryPolicy;
    }

    @Data
    public static class PriceOverrides {
        private Double base; // optional override of product.price
        private List<String> priceBooks; // pb:consumer-eu-tiered-2025
    }

    @Data
    public static class InventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    /**
     * Bundle configurations for kits and virtual bundles
     */
    @Data
    public static class Bundle {
        private String type; // kit (shipped together) or bundle (virtual)
        private String sku;
        private List<BundleComponent> components;
    }

    @Data
    public static class BundleComponent {
        private ProductRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<BundleConstraint> constraints;
        private List<BundleSubstitution> substitutions;
    }

    @Data
    public static class ProductRef {
        private String sku;
    }

    @Data
    public static class BundleConstraint {
        private Map<String, Object> ifVariant; // ifVariant: { option: "capacity", in: ["512GB"] }
        private Map<String, Boolean> then; // then: { forbid: true }
    }

    @Data
    public static class BundleSubstitution {
        private String sku;
        private List<String> whenRegionIn; // US
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
        private String nodeId; // LON-01
        private String type; // Warehouse, 3PL
        private NodeCapacity capacity;
        private Integer qtyOnHand; // for 3PL nodes
        private List<InventoryLot> lots;
        private List<InventoryReservation> reservations;
        private List<InventoryInTransit> inTransit;
    }

    @Data
    public static class NodeCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class InventoryLot {
        private String lotId;
        private String mfgDate; // 2025-01-10
        private String expires; // 2027-01-10
        private Integer qty;
        private List<String> serials; // S100...
        private String quality; // Released, Quarantine
        private String reason; // inspection
    }

    @Data
    public static class InventoryReservation {
        private String ref; // order:O-12345
        private String variantSku;
        private Integer qty;
        private String until; // 2025-09-15T18:00:00Z
    }

    @Data
    public static class InventoryInTransit {
        private String po; // PO-998
        private String eta; // 2025-09-05T12:00:00Z
        private Integer qty;
        private String status; // Scheduled
    }

    @Data
    public static class InventoryPolicies {
        private String allocation; // earliest-expiry-first
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
        private List<ComplianceRestriction> restrictions;
    }

    @Data
    public static class ComplianceDoc {
        private String id; // MSDS-2025-01
        private List<String> regions; // EU, US
        private String url;
        private Boolean approved;
    }

    @Data
    public static class ComplianceRestriction {
        private String region; // CA
        private List<String> rules; // noAirTransport
        private String reason; // Lithium content
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
        private String partyId; // SUP-FOXLINK
        private SupplierContract contract;
    }

    @Data
    public static class SupplierContract {
        private String id; // C-2025-07
        private String incoterm; // DAP
        private Integer leadTimeDays;
    }

    @Data
    public static class RelatedProduct {
        private String type; // accessory, replacement
        private String sku;
    }

    /**
     * Product lifecycle events
     */
    @Data
    public static class Event {
        private String type; // ProductCreated, InventoryReceived, ReservationCreated
        private String at; // 2025-08-20T09:00:00Z
        private Map<String, Object> payload;
    }
}
